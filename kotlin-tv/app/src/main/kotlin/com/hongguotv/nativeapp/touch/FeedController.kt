// SPDX-License-Identifier: GPL-3.0-only
package com.hongguotv.nativeapp.touch

import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import com.hongguotv.core.*
import com.hongguotv.nativeapp.CodecCapabilities
import com.hongguotv.nativeapp.FeedBackend
import com.hongguotv.nativeapp.TrialClip
import com.hongguotv.nativeapp.TrialPlayback
import com.hongguotv.nativeapp.TrialStatus
import com.hongguotv.nativeapp.diagnosticDetail
import com.hongguotv.nativeapp.playbackFailureSummary
import com.hongguotv.nativeapp.shouldSelfHealTrial
import java.io.Closeable
import java.util.concurrent.Executors

/**
 * View-free home feed engine: paging, trial preparation, preloading, caching and
 * recovery. The TV [com.hongguotv.nativeapp.HomeFeedScreen] and the touch
 * [HomePage] only render what the listener reports.
 */
internal class FeedController(
    private val state: DramaFeedState,
    private val backend: FeedBackend,
    private val playback: TrialPlayback,
    private val autoPreview: Boolean,
    private val listener: Listener,
) : Closeable {
    interface Listener {
        /** The visible series changed (null while nothing is available). */
        fun onSeries(series: Series?, index: Int)

        /** Transient status line; null clears it. */
        fun onStatus(message: String?)

        fun onPlaying(playing: Boolean)

        fun onProgress(positionMs: Long, durationMs: Long)

        /** The prepared trial clip changed; null when it was released. */
        fun onTrial(clip: TrialClip?)

        /** Loading, failure, pause or recovery flags changed. */
        fun onChange()

        /** The trial reached its end; the TV screen opens its action bar. */
        fun onEnded() = Unit
    }

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newFixedThreadPool(2)
    private var generation = 0
    private var scope: RequestScope? = null
    private var job: java.util.concurrent.Future<*>? = null
    private var prefetchScope: RequestScope? = null
    private var prefetchJob: java.util.concurrent.Future<*>? = null
    private var prefetchTarget: String? = null

    private data class CachedTrial(
        val clip: TrialClip,
        val savedAt: Long = android.os.SystemClock.elapsedRealtime(),
    )

    private val cachedTrials = linkedMapOf<String, CachedTrial>()
    private val attemptedPrefetch = mutableSetOf<String>()
    private var prefetchGeneration = 0
    private var attemptedBatch = 0
    private var prefetchSeries: String? = null
    private var prefetchReadyAt = 0L
    private var closed = false
    private var manuallyStarted: String? = null
    private var failed = false
    private var trialRecoveryAttempts = 0
    private var trialPreparationUntil = 0L
    private var trialRecovery: Runnable? = null
    private val trialRecoveryDelays = listOf(2000L, 5000L)
    private val exhaustedCursors = mutableSetOf<RecommendationCursor>()
    private var lastPlaybackError: androidx.media3.common.PlaybackException? = null
    private var healTarget: String? = null
    private var healAttempts = 0
    private var pendingTrial: Runnable? = null

    var clip: TrialClip? = null
        private set
    var loading = false
        private set
    var active = false
        private set

    val current: Series? get() = state.current
    val isFailed: Boolean get() = failed
    val recovering: Boolean get() = trialRecovery != null

    init {
        // 播放器级错误先于状态回调到达，供解码能力学习使用。
        playback.onError = { onPlaybackError(it) }
        playback.status = { onPlaybackStatus(it) }
    }

    fun attachStage(stage: FrameLayout) {
        val view = playback.view
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        stage.addView(view, 0, FrameLayout.LayoutParams(-1, -1))
    }

    fun start(foreground: Boolean) {
        if (foreground) resume()
    }

    fun setForeground(value: Boolean) {
        if (value) resume() else pause()
    }

    fun next() = move(1)

    fun previous() = move(-1)

    fun setCategory(category: DramaCategory?) {
        if (closed) return
        if (category == null) {
            if (state.recommendation) return
            clearCurrent()
            clearCache()
            state.recommend()
            publishSeries()
            if (state.current != null) loadCurrent() else loadPage(false)
        } else {
            if (!state.recommendation && state.category == category) return
            clearCurrent()
            state.select(category)
            clearCache()
            publishSeries()
            if (state.current != null) loadCurrent() else loadPage(false)
        }
    }

    fun refresh() {
        if (closed) return
        clearCurrent()
        clearCache()
        val restart = exhaustedCursors.remove(state.recommendationCursor)
        state.refresh(restartCurrentSource = restart)
        publishSeries()
        loadPage(false, resetSource = restart)
    }

    fun togglePause() {
        if (closed) return
        if (loading && trialRecovery == null) return
        // 倒计时期间允许主动立即重试；取消旧定时任务，避免随后再次重复读取。
        if (trialRecovery != null) cancelRequest()
        manuallyStarted = state.current?.id
        if (failed || state.current == null || clip == null) {
            if (state.current == null) state.refresh()
            state.userPaused = false
            state.ended = false
            if (state.current == null) loadPage(false) else loadCurrent()
        } else if (state.ended) {
            state.position = 0
            state.ended = false
            state.userPaused = false
            playback.restart()
        } else {
            state.userPaused = !state.userPaused
            if (state.userPaused) {
                remember()
                playback.pause()
            } else if (active) playback.resume()
        }
        listener.onPlaying(active && !state.userPaused && !state.ended && clip != null)
        listener.onStatus(if (state.userPaused) "试播已暂停" else null)
        listener.onChange()
    }

    /** 收藏等外部操作暂停试播，但保持当前页的前后台状态。 */
    fun pausePlayback() {
        remember()
        playback.pause()
        listener.onPlaying(false)
        listener.onChange()
    }

    private val prefetchPoll =
        object : Runnable {
            override fun run() {
                if (!active || closed) return
                listener.onProgress(playback.position(), playback.duration())
                prefetchUpcoming()
                main.postDelayed(this, 250)
            }
        }

    private fun resume() {
        if (closed || active) return
        active = true
        main.removeCallbacks(prefetchPoll)
        main.postDelayed(prefetchPoll, 250)
        if (clip != null) {
            if (!state.userPaused && !state.ended) playback.resume()
            listener.onPlaying(active && !state.userPaused && !state.ended && clip != null)
            listener.onChange()
            prefetchUpcoming()
        } else if (state.current != null) loadCurrent() else loadPage(false)
    }

    private fun pause() {
        if (closed) return
        remember()
        active = false
        main.removeCallbacks(prefetchPoll)
        playback.pause()
        cancelRequest()
        cancelPrefetch()
        listener.onPlaying(false)
        listener.onChange()
    }

    private fun publishSeries() = listener.onSeries(state.current, state.index)

    private fun remember() {
        if (clip != null) state.position = playback.position()
    }

    private fun cancelRequest() {
        pendingTrial?.let(main::removeCallbacks)
        pendingTrial = null
        trialRecovery?.let(main::removeCallbacks)
        trialRecovery = null
        generation++
        scope?.cancel()
        scope = null
        job?.cancel(true)
        job = null
        loading = false
    }

    private fun cancelPrefetch() {
        prefetchGeneration++
        prefetchScope?.cancel()
        prefetchScope = null
        prefetchJob?.cancel(true)
        prefetchJob = null
        prefetchTarget = null
        attemptedPrefetch.clear()
        attemptedBatch = 0
        prefetchSeries = null
        prefetchReadyAt = 0
    }

    private fun closeCached(cached: CachedTrial) {
        playback.remove(cached.clip.detail.series.id)
        cached.clip.close()
    }

    private fun clearCache() {
        cachedTrials.values.forEach(::closeCached)
        cachedTrials.clear()
    }

    private fun clearCurrent(
        retain: Boolean = false,
        keepPlayer: Boolean = false,
        keepPrefetch: Boolean = false,
    ) {
        remember()
        cancelRequest()
        if (!keepPrefetch) cancelPrefetch()
        if (!keepPlayer) playback.clear()
        clip?.let {
            if (retain && !failed) {
                cachedTrials.put(it.detail.series.id, CachedTrial(it))?.let(::closeCached)
            } else it.close()
        }
        clip = null
        listener.onTrial(null)
    }

    private fun takeCached(id: String): TrialClip? {
        val cached = cachedTrials.remove(id) ?: return null
        if (android.os.SystemClock.elapsedRealtime() - cached.savedAt > 90_000) {
            closeCached(cached)
            return null
        }
        return cached.clip
    }

    // 试播自愈：解码类失败写入设备能力经验表并自动换低档重载（每剧至多 3 轮，且仅在学到
    // 新失败键时继续，同一键反复重载没有意义）。重载复用 loadCurrent（缓存/预取优先），与
    // 手动重试成本相同。bytevc2 锁定集学完 1080P 键后，重载即落入确定性报错，不无限循环。
    private fun selfHealTrial(): Boolean {
        val series = state.current ?: return false
        val track = clip?.track
        if (healTarget != series.id) {
            healTarget = series.id
            healAttempts = 0
        }
        if (
            !shouldSelfHealTrial(
                lastPlaybackError?.errorCode,
                track != null,
                healAttempts,
            )
        )
            return false
        val knownBefore = CodecCapabilities.learnedCount()
        CodecCapabilities.learnFailure(track!!.fourcc, track.width, track.height)
        if (CodecCapabilities.learnedCount() == knownBefore) return false
        healAttempts += 1
        loadCurrent("设备不支持该清晰度，正在自动切换…")
        return true
    }

    // 只对当前作品的播放信息空响应恢复两次，不重试目录、确定性错误或后台预加载。
    // 每次仍沿用 Repository 的有界请求；切换、暂停和关闭通过 cancelRequest 取消倒计时。
    private fun recoverEmptyTrial(problem: Throwable): Boolean {
        if (generateSequence(problem) { it.cause }.take(8).none { it is EmptyPlaybackResponseException }) return false
        val delay = trialRecoveryDelays.getOrNull(trialRecoveryAttempts) ?: return false
        if (trialPreparationUntil - android.os.SystemClock.elapsedRealtime() <= delay) return false
        val target = state.current?.id ?: return false
        val token = generation
        val attempt = ++trialRecoveryAttempts
        val deadline = android.os.SystemClock.elapsedRealtime() + delay
        loading = true
        failed = false
        val retry =
            object : Runnable {
                override fun run() {
                    if (closed || !active || token != generation || state.current?.id != target) return
                    val remaining = deadline - android.os.SystemClock.elapsedRealtime()
                    if (remaining <= 0) {
                        trialRecovery = null
                        loadCurrent("正在恢复试播 · 第 $attempt/${trialRecoveryDelays.size} 次", recovering = true)
                    } else {
                        listener.onStatus(
                            "播放信息暂时为空 · ${(remaining + 999) / 1000} 秒后自动恢复 $attempt/${trialRecoveryDelays.size}",
                        )
                        main.postDelayed(this, minOf(1000L, remaining))
                    }
                }
            }
        trialRecovery = retry
        retry.run()
        listener.onChange()
        return true
    }

    // 后台发起请求，主线程更新界面；页面关闭、切换或暂停后丢弃过期结果。
    private fun <T> request(
        action: () -> T,
        discard: (T) -> Unit = {},
        recoverTrial: Boolean = false,
        done: (T) -> Unit,
    ) {
        cancelRequest()
        loading = true
        val token = generation
        val requestedCursor = state.recommendationCursor
        val requestScope = RequestScope()
        scope = requestScope
        job =
            worker.submit {
                try {
                    val result =
                        requestScope.run {
                            if (!recoverTrial) action()
                            else {
                                val remaining = trialPreparationUntil - android.os.SystemClock.elapsedRealtime()
                                if (remaining <= 0) throw java.net.SocketTimeoutException("试播准备超时")
                                RequestScope.withDeadline(
                                    System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(remaining),
                                    action,
                                )
                            }
                        }
                    main.post {
                        if (closed || token != generation || !active) discard(result)
                        else {
                            loading = false
                            done(result)
                        }
                    }
                } catch (error: Exception) {
                    main.post {
                        if (closed || token != generation || !active) return@post
                        loading = false
                        if (recoverTrial && recoverEmptyTrial(error)) return@post
                        failed = true
                        val message =
                            error.message.orEmpty().replace(Regex("https?://\\S+"), "[地址]").take(90)
                        listener.onStatus(
                            when {
                                error is NoNewRecommendationsException -> {
                                    exhaustedCursors += requestedCursor
                                    "推荐接口未返回新作品 · 最近已去重 ${error.rememberedCount} 部\n可稍后换一批，或切换漫剧、真人剧继续发现"
                                }
                                recoverTrial && trialRecoveryAttempts > 0 -> {
                                    val detail = diagnosticDetail(error)?.take(180)
                                    "试播恢复未成功 · ${playbackFailureSummary(error)}\n可重试试播或换一部" +
                                        (detail?.let { "\n$it" } ?: "")
                                }
                                else -> "暂时无法加载 · ${message.ifBlank { "请重试或换一部" }}"
                            },
                        )
                        listener.onChange()
                    }
                }
            }
    }

    // 按当前推荐模式请求下一页；结果写入状态后，再加载当前作品的试播资源。
    private fun loadPage(advance: Boolean, resetSource: Boolean = false) {
        if (!active || closed || loading) return
        if (state.page >= 100 || (!state.hasMore && state.current != null)) {
            listener.onStatus("本批作品已看完，可切换类型继续发现")
            return
        }
        listener.onStatus("正在读取热门作品…")
        val page = state.page + 1
        val category = state.category
        val recommendation = state.recommendation
        val cursor = state.recommendationCursor
        request({
            if (resetSource) backend.resetRecommendations(if (recommendation) null else category)
            if (recommendation) backend.recommendations(page, cursor)
            else backend.categoryRecommendations(category, page, cursor)
        }) { result ->
            state.append(result, page)
            if (advance) state.move(1)
            if (state.current == null) {
                listener.onStatus("暂时没有可试播的作品，可切换类型或重试")
                failed = true
                listener.onChange()
            } else loadCurrent()
        }
    }

    // 优先复用缓存或正在进行的预加载，避免为同一部作品重复请求。
    private fun loadCurrent(statusHint: String? = null, recovering: Boolean = false, settle: Boolean = false) {
        val series = state.current ?: return
        if (!active || closed) return
        if (!recovering) {
            trialRecoveryAttempts = 0
            trialPreparationUntil = android.os.SystemClock.elapsedRealtime() + 15_000
        }
        // 记下展示过的作品：冷启动时用它跳过重复内容，而不是每次都从同一部开始。
        noteDisplayed(series)
        clearCurrent(keepPrefetch = prefetchTarget == series.id)
        failed = false
        publishSeries()
        listener.onStatus(statusHint ?: "正在准备第一集试播…")
        trimCache()
        val cached = takeCached(series.id)
        if (cached != null) attach(cached)
        else if (prefetchTarget == series.id && prefetchJob != null) {
            // 将正在进行的预加载转为当前试播，不取消后再重复请求地址。
            loading = true
            android.util.Log.i("HongguoTV", "Home promoted in-flight preload")
        } else if (settle) {
            // 快速连刷只更新卡片，停留三百毫秒后才发起未缓存作品的播放准备。
            loading = true
            val token = generation
            val pending =
                Runnable {
                    pendingTrial = null
                    if (!closed && active && token == generation && state.current?.id == series.id)
                        request({ backend.trial(series) }, { it.close() }, recoverTrial = true, done = ::attach)
                }
            pendingTrial = pending
            main.postDelayed(pending, 300)
        } else request({ backend.trial(series) }, { it.close() }, recoverTrial = true, done = ::attach)
    }

    private fun attach(next: TrialClip) {
        if (!autoPreview && manuallyStarted != next.detail.series.id) state.userPaused = true
        clip = next
        listener.onTrial(next)
        listener.onSeries(state.current, state.index)
        val autoplay = active && !state.userPaused && !state.ended
        playback.setCurrentIndex(state.offset + state.index)
        if (!playback.select(next, state.position, autoplay)) {
            playback.load(next, state.position, autoplay)
            cachedTrials.values.forEach { queueCached(it.clip) }
        }
        listener.onPlaying(autoplay)
        listener.onChange()
    }

    private fun noteDisplayed(series: Series) =
        backend.noteDisplayed(
            series,
            state.recommendationCursor,
            if (state.recommendation) null else state.category,
        )

    private fun queueCached(clip: TrialClip) {
        val index = state.items.indexOfFirst { it.id == clip.detail.series.id }
        if (index >= 0) playback.queue(clip, state.offset + index)
    }

    private fun trimCache() {
        val allowed =
            state.items
                .subList(
                    (state.index - 1).coerceAtLeast(0),
                    minOf(state.index + 4, state.items.size),
                )
                .map { it.id }
                .toSet()
        val now = android.os.SystemClock.elapsedRealtime()
        val stale =
            cachedTrials
                .filter { it.key !in allowed || now - it.value.savedAt > 90_000 }
                .keys
                .toList()
        stale.forEach { cachedTrials.remove(it)?.let(::closeCached) }
    }

    /** 保留前一部与后三部缓存；当前就绪后先准备下一部，稳定三秒、六秒后再扩展。 */
    private fun prefetchUpcoming() {
        if (
            closed ||
                !active ||
                clip == null ||
                failed ||
                loading ||
                prefetchJob != null
        )
            return
        if (!playback.canPrefetch()) {
            prefetchReadyAt = android.os.SystemClock.elapsedRealtime()
            return
        }
        trimCache()
        val now = android.os.SystemClock.elapsedRealtime()
        if (prefetchSeries != state.current?.id) {
            prefetchSeries = state.current?.id
            prefetchReadyAt = now
        }
        val depth =
            when {
                now - prefetchReadyAt >= 6000 -> 3
                now - prefetchReadyAt >= 3000 -> 2
                else -> 1
            }
        val upcoming = state.items.drop(state.index + 1).take(3)
        val next = upcoming.take(depth).firstOrNull { it.id !in cachedTrials && it.id !in attemptedPrefetch }
        if (next == null) {
            if (
                upcoming.size < depth &&
                    state.hasMore &&
                    state.page < 100 &&
                    attemptedBatch != state.page + 1
            )
                prefetchBatch()
            return
        }
        attemptedPrefetch += next.id
        prefetchTarget = next.id
        val token = prefetchGeneration
        val requestScope = RequestScope()
        prefetchScope = requestScope
        prefetchJob =
            worker.submit {
                val prepared =
                    runCatching {
                        requestScope.run {
                            RequestScope.withDeadline(
                                System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5),
                            ) {
                                backend.preload(next)
                            }
                        }
                    }.getOrNull()
                main.post {
                    if (closed || !active || token != prefetchGeneration) prepared?.close()
                    else {
                        prefetchJob = null
                        prefetchScope = null
                        prefetchTarget = null
                        if (state.current?.id == next.id && clip == null) {
                            loading = false
                            if (prepared != null) attach(prepared) else loadCurrent(recovering = true)
                        } else if (prepared != null) {
                            cachedTrials.put(next.id, CachedTrial(prepared))?.let(::closeCached)
                            queueCached(prepared)
                        }
                        prefetchUpcoming()
                    }
                }
            }
    }

    private fun prefetchBatch() {
        val page = state.page + 1
        attemptedBatch = page
        val category = state.category
        val recommendation = state.recommendation
        val cursor = state.recommendationCursor
        val token = prefetchGeneration
        val requestScope = RequestScope()
        prefetchScope = requestScope
        prefetchJob =
            worker.submit {
                val result =
                    runCatching {
                            requestScope.run {
                                if (recommendation) backend.recommendations(page, cursor)
                                else backend.categoryRecommendations(category, page, cursor)
                            }
                        }
                        .getOrNull()
                main.post {
                    if (closed || !active || token != prefetchGeneration) return@post
                    prefetchJob = null
                    prefetchScope = null
                    if (result != null && page == state.page + 1) {
                        state.append(result, page)
                        prefetchUpcoming()
                    }
                }
            }
    }

    private fun move(delta: Int) {
        if (closed || !active) return
        if (delta < 0 && state.index == 0) {
            listener.onStatus("已到本次浏览的最早作品")
            return
        }
        if (delta > 0 && state.next == null) {
            if (loading) return
            if (!state.hasMore) {
                listener.onStatus("本批作品已看完，可切换类型继续发现")
                return
            }
            clearCurrent()
            loadPage(true)
            return
        }
        val target = state.items.getOrNull(state.index + delta) ?: return
        val ready = takeCached(target.id)
        clearCurrent(
            retain = true,
            keepPlayer = ready != null,
            keepPrefetch = target.id == prefetchTarget,
        )
        if (state.move(delta)) {
            if (ready != null) {
                noteDisplayed(target)
                failed = false
                publishSeries()
                listener.onStatus("正在切换作品…")
                attach(ready)
                trimCache()
            } else loadCurrent(settle = true)
        }
    }

    /** 播放器状态回调：直接驱动界面提示与动作栏。 */
    fun onPlaybackStatus(value: TrialStatus) {
        if (closed) return
        if (clip == null) return
        when (value) {
            TrialStatus.BUFFERING -> listener.onStatus("正在加载第一集…")
            TrialStatus.READY -> {
                failed = false
                listener.onStatus(if (state.userPaused) "试播已暂停" else null)
                listener.onPlaying(active && !state.userPaused && !state.ended)
                listener.onChange()
                prefetchUpcoming()
            }
            TrialStatus.ENDED -> {
                listener.onStatus("本集试播结束 · 看全集或换一部")
                remember()
                state.ended = true
                state.userPaused = true
                listener.onPlaying(false)
                listener.onEnded()
                listener.onChange()
            }
            TrialStatus.ERROR -> {
                playback.pause()
                listener.onPlaying(false)
                if (!selfHealTrial()) {
                    failed = true
                    listener.onStatus("试播暂不可用 · 可重试、换剧或查看全集")
                }
                listener.onChange()
            }
        }
    }

    /** 播放器级错误：供解码经验学习，仍需状态回调触发自愈。 */
    fun onPlaybackError(error: androidx.media3.common.PlaybackException) {
        lastPlaybackError = error
    }

    override fun close() {
        if (closed) return
        main.removeCallbacks(prefetchPoll)
        clearCurrent()
        clearCache()
        closed = true
        active = false
        playback.release()
        backend.close()
        worker.shutdownNow()
        listener.onPlaying(false)
    }
}
