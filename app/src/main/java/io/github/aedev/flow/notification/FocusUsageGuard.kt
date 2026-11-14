package io.github.aedev.flow.notification

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import dagger.Lazy
import io.github.aedev.flow.data.focus.FocusController
import io.github.aedev.flow.data.focus.FocusGate
import io.github.aedev.flow.data.focus.FocusMode
import io.github.aedev.flow.data.focus.FocusPolicy
import io.github.aedev.flow.data.focus.LimitState
import io.github.aedev.flow.di.ApplicationScope
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.shorts.ShortsPlayerPool
import io.github.aedev.flow.service.MusicServiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Counts playing time and videos started per focus mode, and stops a leisure mode when its daily time or video
 * allowance runs out. Like [DailyLimitGuard] it only runs while something plays, measures with elapsedRealtime, and
 * leaves playback alone while a car is connected.
 */
@Singleton
@OptIn(UnstableApi::class)
class FocusUsageGuard
    @Inject
    constructor(
        private val controller: FocusController,
        @ApplicationScope private val scope: CoroutineScope,
        private val videoPlayer: Lazy<EnhancedPlayerManager>,
        private val shortsPlayers: Lazy<ShortsPlayerPool>,
    ) {
        fun install() {
            // The main thread, so the players are paused on their own thread.
            scope.launch(Dispatchers.Main.immediate) {
                FocusGate.mode.collectLatest { mode ->
                    if (mode == null) return@collectLatest
                    coroutineScope {
                        launch { countVideos(mode) }
                        playing().distinctUntilChanged().collectLatest { playing ->
                            if (playing) countWhilePlaying(mode)
                        }
                    }
                }
            }
        }

        private fun playing(): Flow<Boolean> {
            val video = videoPlayer.get().playerState.map { it.isPlaying }
            val music = EnhancedMusicPlayerManager.playerState.map { it.isPlaying }
            return combine(video, shortsPlayers.get().anyPlaying, music) { v, s, m -> v || s || m }
        }

        /** Each new video or Short that starts in [mode] counts once; past the allowance it is stopped instead. */
        private suspend fun countVideos(mode: FocusMode) {
            val videos =
                videoPlayer
                    .get()
                    .playerState
                    .map { it.currentVideoId }
                    .filterNotNull()
            val shorts = shortsPlayers.get().currentVideoId.filterNotNull()
            merge(videos, shorts).distinctUntilChanged().collect {
                if (!controller.startVideo(mode)) stop(mode, LimitState.VideosReached)
            }
        }

        private suspend fun countWhilePlaying(mode: FocusMode) {
            var since = now()
            try {
                while (true) {
                    val state = FocusPolicy.limitState(mode, controller.today())
                    if (state !is LimitState.Within) {
                        stop(mode, state)
                        awaitCancellation()
                    }
                    delay(minOf(state.remainingMs ?: FLUSH_MS, FLUSH_MS).coerceAtLeast(MIN_DELAY_MS))
                    val at = now()
                    controller.addPlayed(mode.id, at - since)
                    since = at
                }
            } finally {
                withContext(NonCancellable) { controller.addPlayed(mode.id, now() - since) }
            }
        }

        private fun stop(
            mode: FocusMode,
            state: LimitState,
        ) {
            controller.reachLimit(mode, state)
            if (MusicServiceState.isCarConnected) return
            videoPlayer.get().pause()
            shortsPlayers.get().pauseAll()
            EnhancedMusicPlayerManager.pause()
        }

        private fun now(): Long = SystemClock.elapsedRealtime()

        private companion object {
            /** Played time is booked at least this often, so the stats and the limit stay close to the truth. */
            const val FLUSH_MS = 60_000L
            const val MIN_DELAY_MS = 1_000L
        }
    }
