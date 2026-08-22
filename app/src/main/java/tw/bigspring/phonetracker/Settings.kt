package tw.bigspring.phonetracker

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
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

/** Lens values driven from the on-screen faders and mirrored by the CRYENGINE Cinematic Camera. */
data class LensState(
    val focalLengthMm: Float = 50f,
    val aperture: Float = 5.6f,
    val focusDistanceM: Float = 5f,
    val ev: Int = 0,
    val iso: Int = 400,
    val depthOfField: Boolean = true,
    val exposure: Boolean = true,
)

class SettingsRepository(private val context: Context) {
    private object Key {
        val host = stringPreferencesKey("host")
        val port = intPreferencesKey("port")
        val transport = stringPreferencesKey("transport")
        val rateHz = intPreferencesKey("rate_hz")
        val focalLength = floatPreferencesKey("focal_length")
        val aperture = floatPreferencesKey("aperture")
        val focusDistance = floatPreferencesKey("focus_distance")
        val ev = intPreferencesKey("ev")
        val iso = intPreferencesKey("iso")
        val dof = booleanPreferencesKey("dof")
        val exposure = booleanPreferencesKey("exposure")
    }
    val settings: Flow<TrackerSettings> = context.trackerDataStore.data.map { p ->
        TrackerSettings(p[Key.host] ?: "192.168.5.194", p[Key.port] ?: 9050,
            p[Key.transport] ?: "UDP", p[Key.rateHz] ?: 0)
    }
    val lens: Flow<LensState> = context.trackerDataStore.data.map { p ->
        LensState(p[Key.focalLength] ?: 50f, p[Key.aperture] ?: 5.6f, p[Key.focusDistance] ?: 5f,
            p[Key.ev] ?: 0, p[Key.iso] ?: 400, p[Key.dof] ?: true, p[Key.exposure] ?: true)
    }
    suspend fun saveLens(value: LensState) = context.trackerDataStore.edit { p ->
        p[Key.focalLength] = value.focalLengthMm; p[Key.aperture] = value.aperture
        p[Key.focusDistance] = value.focusDistanceM; p[Key.ev] = value.ev; p[Key.iso] = value.iso
        p[Key.dof] = value.depthOfField; p[Key.exposure] = value.exposure
    }

    suspend fun save(value: TrackerSettings) = context.trackerDataStore.edit { p ->
        p[Key.host] = value.host; p[Key.port] = value.port
        p[Key.transport] = value.transport; p[Key.rateHz] = value.rateHz
    }
}
