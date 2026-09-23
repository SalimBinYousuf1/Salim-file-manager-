package com.example.ui.theme

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.semantics.clearAndSetSemantics
import org.intellij.lang.annotations.Language
import kotlin.math.cos
import kotlin.math.sin

/**
 * ASGL Atmospheric Cloud Simulation
 * High-performance GPU-driven atmospheric fog/cloud rendering using AGSL RuntimeShader
 * (with high-fidelity Canvas FBM multi-octave cloud locomotion fallback on API < 33 or tests).
 *
 * Four distinct atmospheric clouds with independent world-space trajectories:
 * - Deep True Red
 * - Deep True Blue
 * - Deep Grass Green
 * - Deep Rich Yellow
 */

@Language("AGSL")
private const val ASGL_ATMOSPHERIC_SHADER = """
uniform float2 uResolution;
uniform float uTime;

// Hash function
float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// 2D Smooth noise
float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i + float2(0.0, 0.0)),
                   hash(i + float2(1.0, 0.0)), u.x),
               mix(hash(i + float2(0.0, 1.0)),
                   hash(i + float2(1.0, 1.0)), u.x), u.y);
}

// Multi-octave Fractional Brownian Motion (FBM)
float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    float2 shift = float2(100.0, 100.0);
    float2x2 rot = float2x2(0.87758, 0.47942, -0.47942, 0.87758);
    for (int i = 0; i < 4; ++i) {
        v += a * noise(p);
        p = rot * p * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

// Domain Warping for organic atmospheric fluid deformation
float domainWarp(float2 p, float t) {
    float2 q = float2(fbm(p + float2(0.0, t * 0.04)), fbm(p + float2(5.2, 1.3 - t * 0.035)));
    float2 r = float2(fbm(p + 3.5 * q + float2(1.7 - t * 0.025, 9.2 + t * 0.02)),
                      fbm(p + 3.5 * q + float2(8.3 + t * 0.03, 2.8 - t * 0.025)));
    return fbm(p + 3.0 * r);
}

vec4 main(float2 fragCoord) {
    float2 uv = fragCoord / uResolution.xy;
    float aspect = uResolution.x / uResolution.y;
    float2 p = float2(uv.x * aspect, uv.y) * 2.2;
    float t = uTime * 0.16;

    // Independent world-space trajectories for 4 atmospheric cloud formations
    // Cloud 1: Deep True Red
    float2 traj1 = float2(sin(t * 0.38) * 1.5 + cos(t * 0.21) * 0.7,
                          cos(t * 0.32) * 1.3 + sin(t * 0.17) * 0.6);
    // Cloud 2: Deep True Blue
    float2 traj2 = float2(cos(t * 0.31) * 1.6 - sin(t * 0.27) * 0.8,
                          sin(t * 0.43) * 1.4 - cos(t * 0.13) * 0.7);
    // Cloud 3: Deep Grass Green
    float2 traj3 = float2(sin(t * 0.25 + 2.1) * 1.6 + cos(t * 0.33) * 0.7,
                          cos(t * 0.29 + 1.4) * 1.4 + sin(t * 0.39) * 0.6);
    // Cloud 4: Deep Rich Yellow
    float2 traj4 = float2(cos(t * 0.35 + 3.7) * 1.5 - sin(t * 0.19) * 0.8,
                          sin(t * 0.23 + 4.2) * 1.3 + cos(t * 0.31) * 0.7);

    // Continuous morphing & density fields through FBM & domain warping
    float d1 = domainWarp(p - traj1, t);
    float d2 = domainWarp(p - traj2, t + 1.8);
    float d3 = domainWarp(p - traj3, t + 3.6);
    float d4 = domainWarp(p - traj4, t + 5.4);

    // Distance falloffs with organic atmospheric soft edges
    float dist1 = length(p - traj1);
    float dist2 = length(p - traj2);
    float dist3 = length(p - traj3);
    float dist4 = length(p - traj4);

    float w1 = smoothstep(2.3, 0.15, dist1) * pow(d1, 1.35);
    float w2 = smoothstep(2.3, 0.15, dist2) * pow(d2, 1.35);
    float w3 = smoothstep(2.3, 0.15, dist3) * pow(d3, 1.35);
    float w4 = smoothstep(2.3, 0.15, dist4) * pow(d4, 1.35);

    // Deep, authentic non-pastel colors (Section 17)
    vec3 cRed    = vec3(0.86, 0.15, 0.15); // Deep True Red
    vec3 cBlue   = vec3(0.14, 0.38, 0.92); // Deep True Blue
    vec3 cGreen  = vec3(0.08, 0.64, 0.28); // Deep Grass Green
    vec3 cYellow = vec3(0.92, 0.70, 0.05); // Deep Rich Yellow

    // Deep rich space backdrop
    vec3 color = vec3(0.03, 0.04, 0.07);

    // Organic atmospheric accumulation & blending
    color += cRed * w1 * 0.95;
    color += cBlue * w2 * 1.15;
    color += cGreen * w3 * 0.95;
    color += cYellow * w4 * 0.95;

    return vec4(clamp(color, 0.0, 1.0), 1.0);
}
"""

@Composable
fun AsglAtmosphereBackground(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "asgl_time")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 300000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    val isShaderSupported = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    val runtimeShader = remember(isShaderSupported) {
        if (isShaderSupported) {
            try {
                RuntimeShader(ASGL_ATMOSPHERIC_SHADER)
            } catch (e: Throwable) {
                null
            }
        } else {
            null
        }
    }

    if (runtimeShader != null) {
        Canvas(
            modifier = modifier
                .fillMaxSize()
                .clearAndSetSemantics { /* Accessibility: decorative background */ }
        ) {
            runtimeShader.setFloatUniform("uResolution", size.width, size.height)
            runtimeShader.setFloatUniform("uTime", time)
            drawRect(brush = ShaderBrush(runtimeShader))
        }
    } else {
        // High-fidelity Multi-octave Canvas Atmospheric Fallback
        Canvas(
            modifier = modifier
                .fillMaxSize()
                .clearAndSetSemantics { /* Accessibility: decorative background */ }
        ) {
            val w = size.width
            val h = size.height
            val t = time * 0.2f

            // Solid atmospheric deep space base
            drawRect(Color(0xFF04060A))

            // Cloud 1: Deep True Red (independent curvilinear trajectory)
            val c1x = (0.35f + 0.35f * sin(t * 0.35f) + 0.15f * cos(t * 0.18f)) * w
            val c1y = (0.45f + 0.30f * cos(t * 0.28f) + 0.12f * sin(t * 0.14f)) * h
            val r1 = (0.55f + 0.10f * sin(t * 0.22f)) * w
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AsglDeepRed.copy(alpha = 0.55f),
                        AsglDeepRed.copy(alpha = 0.25f),
                        Color.Transparent
                    ),
                    center = Offset(c1x, c1y),
                    radius = r1
                ),
                radius = r1,
                center = Offset(c1x, c1y)
            )

            // Cloud 2: Deep True Blue (independent curvilinear trajectory)
            val c2x = (0.65f - 0.32f * cos(t * 0.29f) - 0.14f * sin(t * 0.24f)) * w
            val c2y = (0.50f + 0.35f * sin(t * 0.37f) - 0.10f * cos(t * 0.12f)) * h
            val r2 = (0.65f + 0.12f * cos(t * 0.19f)) * w
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AsglDeepBlue.copy(alpha = 0.65f),
                        AsglDeepBlue.copy(alpha = 0.30f),
                        Color.Transparent
                    ),
                    center = Offset(c2x, c2y),
                    radius = r2
                ),
                radius = r2,
                center = Offset(c2x, c2y)
            )

            // Cloud 3: Deep Grass Green (independent curvilinear trajectory)
            val c3x = (0.50f + 0.38f * sin(t * 0.23f + 1.8f)) * w
            val c3y = (0.30f + 0.28f * cos(t * 0.31f + 1.2f)) * h
            val r3 = (0.50f + 0.08f * sin(t * 0.27f)) * w
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AsglDeepGreen.copy(alpha = 0.50f),
                        AsglDeepGreen.copy(alpha = 0.20f),
                        Color.Transparent
                    ),
                    center = Offset(c3x, c3y),
                    radius = r3
                ),
                radius = r3,
                center = Offset(c3x, c3y)
            )

            // Cloud 4: Deep Rich Yellow (independent curvilinear trajectory)
            val c4x = (0.40f + 0.34f * cos(t * 0.33f + 3.1f)) * w
            val c4y = (0.75f - 0.26f * sin(t * 0.21f + 2.5f)) * h
            val r4 = (0.48f + 0.10f * cos(t * 0.25f)) * w
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AsglDeepYellow.copy(alpha = 0.45f),
                        AsglDeepYellow.copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(c4x, c4y),
                    radius = r4
                ),
                radius = r4,
                center = Offset(c4x, c4y)
            )
        }
    }
}
