package io.github.aedev.flow.data.focus

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Focus modes, the current session and today's per-mode count, in the profile's own settings store. Each is one JSON
 * value, so adding a field to a mode later needs no new keys. A value that fails to read falls back to the presets,
 * no session, or a fresh day, rather than breaking launch.
 */
@Singleton
class FocusStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** Every mode, presets first, with any missing preset put back. */
        val modes: Flow<List<FocusMode>> =
            context.dataStore.data.map { prefs ->
                FocusPresets.merge(prefs[MODES]?.let { decode(it, MODE_LIST) }.orEmpty())
            }

        /** The session the viewer is in, or null before they picked a mode. */
        val session: Flow<FocusSession?> =
            context.dataStore.data.map { prefs -> prefs[SESSION]?.let { decode(it, FocusSession.serializer()) } }

        /** The stored count, or null before anything was counted. */
        val tally: Flow<FocusDayTally?> =
            context.dataStore.data.map { prefs -> prefs[TALLY]?.let { decode(it, FocusDayTally.serializer()) } }

        suspend fun setModes(modes: List<FocusMode>) {
            context.dataStore.edit { prefs -> prefs[MODES] = json.encodeToString(MODE_LIST, FocusPresets.merge(modes)) }
        }

        suspend fun setSession(session: FocusSession?) {
            context.dataStore.edit { prefs ->
                if (session == null) {
                    prefs.remove(SESSION)
                } else {
                    prefs[SESSION] = json.encodeToString(FocusSession.serializer(), session)
                }
            }
        }

        suspend fun setTally(tally: FocusDayTally) {
            context.dataStore.edit { prefs -> prefs[TALLY] = json.encodeToString(FocusDayTally.serializer(), tally) }
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
            private val MODE_LIST = ListSerializer(FocusMode.serializer())

            private val MODES = stringPreferencesKey("focus_modes")
            private val SESSION = stringPreferencesKey("focus_session")
            private val TALLY = stringPreferencesKey("focus_day_tally")

            /** The keys a settings backup carries: the modes the viewer set up. */
            val SETTING_NAMES: Set<String> = setOf(MODES.name)

            /** The running session and count, which a backup leaves behind. */
            val STATE_NAMES: Set<String> = setOf(SESSION.name, TALLY.name)
        }
    }
