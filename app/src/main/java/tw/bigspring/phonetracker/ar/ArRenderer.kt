package tw.bigspring.phonetracker.ar

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.google.ar.core.TrackingState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.roundToInt

class ArRenderer(private val controller: ArSessionController, private val onPose: (PoseSample) -> Unit) : GLSurfaceView.Renderer {
    private var texture = 0; private var lastFrame = -1L; private var lastTick = 0L; private var frames = 0; private var fps = 0; private val preview = CameraPreviewQuad()
    override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) { texture = IntArray(1).also { GLES20.glGenTextures(1, it, 0) }[0]; GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture); GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR); preview.create(); controller.session?.setCameraTextureName(texture) }
    override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) { GLES20.glViewport(0, 0, width, height) }
    override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) { val s = controller.session ?: return; try { val f = s.update(); GLES20.glClearColor(0f,0f,0f,1f); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT); preview.draw(texture); val ts = f.timestamp; if (ts == lastFrame) return; lastFrame = ts; frames++; val now = System.nanoTime(); if (now-lastTick > 1_000_000_000L) { fps = (frames * 1e9 / (now-lastTick).coerceAtLeast(1)).roundToInt(); frames=0; lastTick=now }; val c=f.camera; val p=c.pose; onPose(PoseSample(ts,p.tx(),p.ty(),p.tz(),p.qx(),p.qy(),p.qz(),p.qw(),when(c.trackingState){TrackingState.TRACKING->2;TrackingState.PAUSED->1;else->0}, c.trackingFailureReason.ordinal, 0, fps)) } catch (_: Exception) {} }
}
