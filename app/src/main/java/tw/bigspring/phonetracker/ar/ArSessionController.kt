package tw.bigspring.phonetracker.ar

import android.content.Context
import android.util.Log
import com.google.ar.core.*
import java.util.EnumSet

class ArSessionController(private val context: Context) {
    var session: Session? = null; private set
    fun create() {
        val s = Session(context)
        val filter = CameraConfigFilter(s).setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_60)).setDepthSensorUsage(EnumSet.of(CameraConfig.DepthSensorUsage.DO_NOT_USE))
        val configs = s.getSupportedCameraConfigs(filter)
        s.cameraConfig = configs.minByOrNull { it.imageSize.width * it.imageSize.height } ?: s.getSupportedCameraConfigs(CameraConfigFilter(s)).first()
        Log.i("PhoneTracker", "ARCore camera config: image=${s.cameraConfig.imageSize}, fps=${s.cameraConfig.fpsRange}, 60fpsCandidates=${configs.size}")
        s.configure(Config(s).apply { focusMode = Config.FocusMode.FIXED; planeFindingMode = Config.PlaneFindingMode.DISABLED; lightEstimationMode = Config.LightEstimationMode.DISABLED; depthMode = Config.DepthMode.DISABLED; instantPlacementMode = Config.InstantPlacementMode.DISABLED; updateMode = Config.UpdateMode.BLOCKING })
        session = s
    }
    fun resume() = session?.resume()
    fun pause() = session?.pause()
    fun close() { session?.close(); session = null }
}
