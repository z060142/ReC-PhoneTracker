package tw.bigspring.phonetracker.ar

import android.opengl.GLES11Ext
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal external-OES camera background renderer; no Sceneform dependency. */
class CameraPreviewQuad {
    private val vertices = floatArrayOf(-1f,-1f, 1f,-1f, -1f,1f, 1f,1f); private val texcoords = floatArrayOf(0f,1f, 1f,1f, 0f,0f, 1f,0f)
    private var program = 0; private var pos = 0; private var uv = 0
    private fun shader(type: Int, source: String): Int = GLES20.glCreateShader(type).also {
        GLES20.glShaderSource(it, source)
        GLES20.glCompileShader(it)
    }
    fun create() {
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, shader(GLES20.GL_VERTEX_SHADER,
            "attribute vec2 aPos; attribute vec2 aUv; varying vec2 vUv; void main(){ gl_Position=vec4(aPos,0.0,1.0); vUv=aUv; }"))
        GLES20.glAttachShader(program, shader(GLES20.GL_FRAGMENT_SHADER,
            "#extension GL_OES_EGL_image_external : require\nprecision mediump float; varying vec2 vUv; uniform samplerExternalOES uTex; void main(){ gl_FragColor=texture2D(uTex,vUv); }"))
        GLES20.glLinkProgram(program)
        pos = GLES20.glGetAttribLocation(program, "aPos")
        uv = GLES20.glGetAttribLocation(program, "aUv")
    }
    private fun buffer(a: FloatArray) = ByteBuffer.allocateDirect(a.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(a); position(0) }
    fun draw(texture: Int) { GLES20.glUseProgram(program); GLES20.glDisable(GLES20.GL_DEPTH_TEST); GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture); GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,0,buffer(vertices)); GLES20.glEnableVertexAttribArray(pos); GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,0,buffer(texcoords)); GLES20.glEnableVertexAttribArray(uv); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4); GLES20.glEnable(GLES20.GL_DEPTH_TEST) }
}
