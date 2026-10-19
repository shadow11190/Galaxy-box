package dev.galaxy.toolbox.data

import android.opengl.EGL14
import android.opengl.GLES20

/** Offscreen EGL context; always called on the IO sampling worker, never the UI thread. */
object GpuInfo {
    data class Details(val renderer: String?, val vendor: String?, val version: String?, val shading: String?, val maxTextureSize: Int?, val extensions: String? = null, val maxRenderbufferSize: Int? = null, val maxVertexAttributes: Int? = null, val maxTextureUnits: Int? = null)
    fun read(): Details? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null
        var context = EGL14.EGL_NO_CONTEXT
        var surface = EGL14.EGL_NO_SURFACE
        try {
            val versions = IntArray(2)
            if (!EGL14.eglInitialize(display, versions, 0, versions, 1)) return null
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val count = IntArray(1)
            val attrs = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE)
            if (!EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, count, 0) || count[0] == 0) return null
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            surface = EGL14.eglCreatePbufferSurface(display, configs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return null
            fun string(key: Int) = GLES20.glGetString(key)?.takeIf { it.isNotBlank() }
            val maximum = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maximum, 0)
            fun limit(key: Int): Int? { val value = IntArray(1); GLES20.glGetIntegerv(key, value, 0); return value[0].takeIf { it > 0 } }
            return Details(string(GLES20.GL_RENDERER), string(GLES20.GL_VENDOR), string(GLES20.GL_VERSION),
                string(GLES20.GL_SHADING_LANGUAGE_VERSION), maximum[0].takeIf { it > 0 }, string(GLES20.GL_EXTENSIONS), limit(GLES20.GL_MAX_RENDERBUFFER_SIZE), limit(GLES20.GL_MAX_VERTEX_ATTRIBS), limit(GLES20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS))
        } catch (_: Exception) { return null }
        finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            EGL14.eglReleaseThread()
        }
    }
}
