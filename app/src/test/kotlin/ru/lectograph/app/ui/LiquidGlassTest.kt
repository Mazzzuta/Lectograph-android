package ru.lectograph.app.ui

import android.graphics.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.lectograph.app.ui.components.LIQUID_SHADER

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiquidGlassTest {
    @Test fun shaderCompilesAndAcceptsSurfaceUniforms() {
        val shader = RuntimeShader(LIQUID_SHADER)
        shader.setFloatUniform("screen", 400f, 800f)
        shader.setFloatUniform("origin", 20f, 120f)
        shader.setFloatUniform("bounds", 360f, 240f)
        shader.setFloatUniform("radius", 24f)
        shader.setFloatUniform("light", 0f)
        shader.setFloatUniform("glass", 0f)
        shader.setFloatUniform("glass", 1f)
        shader.setFloatUniform("light", 1f)
    }
}
