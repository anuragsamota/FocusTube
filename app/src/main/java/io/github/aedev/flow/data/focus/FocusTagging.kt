package io.github.aedev.flow.data.focus

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.safePreferencesDataStore
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.profile.StoreScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.focusTagStore by safePreferencesDataStore(name = "focus_tags", scope = StoreScope.PROFILE_PRIVATE)

/**
 * Tags videos for focus modes and remembers what each channel posts. The memory is engine state like FlowNeuro's: it
 * stays on this install and isn't backed up, and it rebuilds itself as feeds load.
 */
@Singleton
class FocusTagging
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val mutex = Mutex()

        @Volatile
        private var memory: FocusChannelTags? = null

        /** The tags of [video], using what its channel usually posts when the video says nothing itself. */
        suspend fun tagsOf(video: Video): Set<FocusTag> = tag(video, load())

        /** Tags for many videos with one read of the channel memory. */
        suspend fun tagsOf(videos: List<Video>): Map<String, Set<FocusTag>> {
            val channels = load()
            return videos.associate { it.id to tag(video = it, channels = channels) }
        }

        /** Learns each channel's habits from [uploads], typically a subscription feed refresh. */
        suspend fun observe(
            uploads: List<Video>,
            now: Long = System.currentTimeMillis(),
        ) {
            if (uploads.isEmpty()) return
            mutex.withLock {
                var channels = load()
                uploads.forEach { video ->
                    val own = FocusTagger.tag(video.category, video.title, video.tags, isMusic = video.isMusic)
                    channels = channels.learn(video.channelId, own, now)
                }
                memory = channels
                context.focusTagStore.edit { it[CHANNELS] = json.encodeToString(FocusChannelTags.serializer(), channels) }
            }
        }

        private fun tag(
            video: Video,
            channels: FocusChannelTags,
        ): Set<FocusTag> =
            FocusTagger.tag(
                category = video.category,
                title = video.title,
                keywords = video.tags,
                channelTags = channels.tagsOf(video.channelId),
                isMusic = video.isMusic,
            )

        private suspend fun load(): FocusChannelTags {
            memory?.let { return it }
            val stored = context.focusTagStore.data.first()[CHANNELS]
            val decoded =
                stored?.let {
                    try {
                        json.decodeFromString(FocusChannelTags.serializer(), it)
                    } catch (e: SerializationException) {
                        Log.w(TAG, "Unreadable channel tags, starting over", e)
                        null
                    } catch (e: IllegalArgumentException) {
                        Log.w(TAG, "Unreadable channel tags, starting over", e)
                        null
                    }
                } ?: FocusChannelTags()
            memory = decoded
            return decoded
        }

        private companion object {
            const val TAG = "FocusTagging"
            val CHANNELS = stringPreferencesKey("channels")
            val json = Json { ignoreUnknownKeys = true }
        }
    }
