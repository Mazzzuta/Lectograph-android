package ru.lectograph.app.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Dp

// The glass refracts the shared procedural wallpaper; foreground text is drawn separately.
internal const val LIQUID_SHADER = """
uniform float2 screen;
uniform float2 origin;
uniform float2 bounds;
uniform float radius;
uniform float glass;
uniform float light;
float3 scene(float2 p) {
    float2 uv = p / screen;
    float wave = uv.x + 0.16*sin(uv.y*7.0);
    float a = exp(-pow((wave-0.78)*3.2,2.0)-pow((uv.y-0.18)*2.3,2.0));
    float b = exp(-pow((wave-0.08)*3.4,2.0)-pow((uv.y-0.65)*2.6,2.0));
    float c = exp(-pow((uv.x-0.9)*3.8,2.0)-pow((uv.y-0.94)*3.5,2.0));
    float3 dark = float3(0.008,0.009,0.012) + a*float3(0.042,0.044,0.05) + b*float3(0.026,0.029,0.034) + c*float3(0.018,0.02,0.025);
    return mix(dark, float3(0.91,0.94,0.98)-a*float3(0.12,0.18,0.02)-b*float3(0.18,0.04,0.06), light);
}
float sd(float2 p) {
    float2 q = abs(p-bounds*0.5)-(bounds*0.5-float2(radius));
    return length(max(q,0.0))+min(max(q.x,q.y),0.0)-radius;
}
half4 main(float2 p) {
    if (glass < 0.5) return half4(scene(p+origin),1.0);
    float d = sd(p);
    float2 n = normalize(float2(sd(p+float2(0.5,0.0))-sd(p-float2(0.5,0.0)),sd(p+float2(0.0,0.5))-sd(p-float2(0.0,0.5)))+float2(0.0001));
    float edge = exp(-max(-d,0.0)/max(radius*0.65,1.0));
    float2 samplePos = origin + p - n*edge*radius*0.48;
    float3 col = scene(samplePos);
    col = mix(col, mix(float3(0.07,0.075,0.085),float3(1.0),light),0.16);
    // Broad lights in screen coordinates avoid repeating a sharp highlight on every corner.
    float2 world = (origin+p)/screen;
    float angle = -0.65 + 0.45*sin(world.y*3.0 + bounds.x/screen.x);
    float2 direction = normalize(float2(sin(angle),-cos(angle)));
    float spec = pow(max(dot(n,direction),0.0),2.0);
    float pool = 0.55 + 0.45*sin(world.x*4.0+world.y*2.5);
    float rim = exp(-abs(d+0.8)*0.85);
    float softEdge = exp(-max(-d,0.0)/max(radius*1.1,1.0));
    col += softEdge*(0.012 + 0.045*spec*pool);
    col += rim*(0.035 + 0.075*spec*pool)*(1.0-light);
    col -= light*rim*0.13;
    col -= light*softEdge*0.035;
    col += float3(0.018,0.020,0.023)*edge*max(n.y,0.0);
    return half4(clamp(col,0.0,1.0),1.0);
}
"""

internal fun Modifier.liquidLayer(radius: Dp, glass: Boolean): Modifier = composed {
    if (Build.VERSION.SDK_INT < 33) return@composed this
    LiquidLayer(this, radius, glass)
}

@RequiresApi(33)
@Composable private fun LiquidLayer(modifier: Modifier, radius: Dp, glass: Boolean): Modifier {
    val view = LocalView.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val shader = remember { RuntimeShader(LIQUID_SHADER) }
    val light = if (MaterialTheme.colorScheme.background.red > 0.5f) 1f else 0f
    return modifier.onGloballyPositioned { origin = it.positionInRoot() }.drawWithCache {
        shader.setFloatUniform("screen", view.width.toFloat().coerceAtLeast(1f), view.height.toFloat().coerceAtLeast(1f))
        shader.setFloatUniform("origin", origin.x, origin.y)
        shader.setFloatUniform("bounds", size.width, size.height)
        shader.setFloatUniform("radius", radius.toPx().coerceAtMost(size.minDimension/2f))
        shader.setFloatUniform("glass", if (glass) 1f else 0f)
        shader.setFloatUniform("light", light)
        val brush = ShaderBrush(shader)
        onDrawBehind { drawRect(brush) }
    }
}
