package io.github.aedev.flow.data.focus

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.safePreferencesDataStore
import io.github.aedev.flow.data.profile.StoreScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.focusStateStore by safePreferencesDataStore(name = "focus_state", scope = StoreScope.DEVICE)

/**
 * The focus session and today's per-profile count, shared by every profile: the lock has to hold across the restart
 * a profile switch makes, and the profile switcher reads it to refuse leaving a locked profile. The rules themselves
 * live on each profile in the registry. A value that fails to read falls back to no session or a fresh day rather
 * than breaking launch. Never backed up: a restored phone starts without a running lock.
 */
@Singleton
class FocusStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** The session the viewer is in, or null before they picked a profile. */
        val session: Flow<FocusSession?> =
            context.focusStateStore.data.map { prefs -> prefs[SESSION]?.let { decode(it, FocusSession.serializer()) } }

        /** The stored count, or null before anything was counted. */
        val tally: Flow<FocusDayTally?> =
            context.focusStateStore.data.map { prefs -> prefs[TALLY]?.let { decode(it, FocusDayTally.serializer()) } }

        suspend fun setSession(session: FocusSession?) {
            context.focusStateStore.edit { prefs ->
                if (session == null) {
                    prefs.remove(SESSION)
                } else {
                    prefs[SESSION] = json.encodeToString(FocusSession.serializer(), session)
                }
            }
        }

        suspend fun setTally(tally: FocusDayTally) {
            context.focusStateStore.edit { prefs -> prefs[TALLY] = json.encodeToString(FocusDayTally.serializer(), tally) }
        }

        private fun <T> decode(
            value: String,
            serializer: kotlinx.serialization.KSerializer<T>,
        ): T? =
            try {
                json.decodeFromString(serializer, value)
            } catch (e: SerializationException) {
                Log.w(TAG, "Unreadable focus value, using the default", e)
                null
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Unreadable focus value, using the default", e)
                null
            }

        companion object {
            private const val TAG = "FocusStore"
            private val json = Json { ignoreUnknownKeys = true }
            private val SESSION = stringPreferencesKey("session")
            private val TALLY = stringPreferencesKey("day_tally")
        }
    }
