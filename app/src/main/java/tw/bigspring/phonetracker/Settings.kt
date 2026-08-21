package tw.bigspring.phonetracker

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.trackerDataStore by preferencesDataStore("tracker_settings")

data class TrackerSettings(
    val host: String = "192.168.5.194",
    val port: Int = 9050,
    val transport: String = "UDP",
    val rateHz: Int = 0,
)

class SettingsRepository(private val context: Context) {
    private object Key {
        val host = stringPreferencesKey("host")
        val port = intPreferencesKey("port")
        val transport = stringPreferencesKey("transport")
        val rateHz = intPreferencesKey("rate_hz")
    }
    val settings: Flow<TrackerSettings> = context.trackerDataStore.data.map { p ->
        TrackerSettings(p[Key.host] ?: "192.168.5.194", p[Key.port] ?: 9050,
            p[Key.transport] ?: "UDP", p[Key.rateHz] ?: 0)
    }
    suspend fun save(value: TrackerSettings) = context.trackerDataStore.edit { p ->
        p[Key.host] = value.host; p[Key.port] = value.port
        p[Key.transport] = value.transport; p[Key.rateHz] = value.rateHz
    }
}
