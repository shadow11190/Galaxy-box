package dev.galaxy.toolbox.ui

import android.graphics.RuntimeShader
import android.os.Build

/** The lens needs AGSL child shaders, not just the presence of RenderEffect. */
internal object GlassCompatibility {
    val refractionSupported: Boolean by lazy {
        Build.VERSION.SDK_INT >= 33 && runCatching {
            RuntimeShader("uniform shader source; half4 main(float2 p) { return source.eval(p); }")
        }.isSuccess
    }
    fun canUse(api: Int, accelerated: Boolean, lowRam: Boolean, powerSave: Boolean, shader: Boolean) =
        api >= 33 && accelerated && !lowRam && !powerSave && shader
}
