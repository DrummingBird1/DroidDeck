package com.droiddeck.launcher.gpu

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.util.Log
import com.droiddeck.launcher.session.SessionPrefs
import java.util.concurrent.atomic.AtomicReference

object TpUbwcHint {
    const val ENV = "FD_DEV_FEATURES"
    private const val TAG = "TpUbwcHint"

    @Volatile private var resolved = false
    @Volatile private var cached: String? = null
    @Volatile var activeMode: String? = null
        private set

    internal fun features(model: Int, systemDriverVersion: String?, samsung: Boolean = false,
                          mode: String = SessionPrefs.TP_UBWC_AUTO, glVersion: () -> String? = { null }): String? {
        if (model != 740) return null
        if (mode == SessionPrefs.TP_UBWC_ON) return "enable_tp_ubwc_flag_hint=1"
        if (mode == SessionPrefs.TP_UBWC_OFF) return "enable_tp_ubwc_flag_hint=0"
        if (samsung) return "enable_tp_ubwc_flag_hint=1"
        val parts = systemDriverVersion?.split('.') ?: return null
        val blob = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val vulkanPatch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        val patch = if (blob == 676) glBuild(blob, glVersion()) ?: vulkanPatch else vulkanPatch
        val on = blob >= 700 || (blob == 676 && patch >= 76)
        return "enable_tp_ubwc_flag_hint=" + if (on) "1" else "0"
    }

    // Qualcomm's Vulkan driverVersion can drop the middle part of a three-part build:
    // the POCO F6 Pro's "OpenGL ES 3.2 V@0676.76.1" blob reports 512.676.1.
    internal fun glBuild(blob: Int, glVersion: String?): Int? {
        val match = Regex("""V@(\d+)\.(\d+)""").find(glVersion ?: return null) ?: return null
        if (match.groupValues[1].toIntOrNull() != blob) return null
        return match.groupValues[2].toIntOrNull()
    }

    @Synchronized
    fun forThisDevice(context: Context): String? {
        if (resolved) return cached
        val mode = SessionPrefs.tpUbwcMode(context)
        val gpu = GpuInfo.detect()
        val model = gpu.model
        val version = if (model == 740) VulkanInfo.query()["driver_version"] else null
        var gl: String? = null
        cached = features(model, version, gpu.oneUi8Gen2, mode) { systemGlVersion().also { gl = it } }
        activeMode = mode
        resolved = true
        Log.i(TAG, "Adreno $model${if (gpu.oneUi8Gen2) " (One UI)" else ""}, system driver ${version ?: "n/a"}${gl?.let { ", GLES $it" } ?: ""}, mode $mode: ${cached ?: "driver default"}")
        return cached
    }

    private fun systemGlVersion(): String? {
        val result = AtomicReference<String?>()
        val probe = Thread({ result.set(runCatching { queryGlVersion() }.getOrNull()) }, "gl-version")
        probe.isDaemon = true
        probe.start()
        probe.join(2000)
        return result.get()
    }

    private fun queryGlVersion(): String? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null
        try {
            val attributes = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE)
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) || count[0] == 0) return null
            val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            if (context == EGL14.EGL_NO_CONTEXT) return null
            val surface = EGL14.eglCreatePbufferSurface(display, configs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            try {
                if (surface == EGL14.EGL_NO_SURFACE || !EGL14.eglMakeCurrent(display, surface, surface, context)) return null
                return GLES20.glGetString(GLES20.GL_VERSION)
            } finally {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                EGL14.eglDestroyContext(display, context)
            }
        } finally {
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        }
    }

    fun restartRequired(context: Context): Boolean = resolved && activeMode != SessionPrefs.tpUbwcMode(context)

    fun applyToProcess(context: Context) {
        val value = forThisDevice(context) ?: return
        runCatching { android.system.Os.setenv(ENV, value, true) }
            .onFailure { Log.w(TAG, "setenv $ENV failed: ${it.javaClass.simpleName}") }
    }
}
