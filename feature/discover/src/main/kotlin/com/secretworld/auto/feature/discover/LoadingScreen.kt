package com.secretworld.auto.feature.discover

import com.secretworld.auto.core.designsystem.CarTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secretworld.auto.core.designsystem.Motion
import kotlin.math.cos
import kotlin.math.sin

/** Qué se está preparando en el primer arranque: cada una es una etapa de la carga. */
enum class LoadingStage { VOICE, LISTENING, ASSISTANT }

/**
 * La pantalla del primer arranque, como la carga de un videojuego: mientras se
 * descomprimen voces y modelos —decenas de segundos, una sola vez— un radar
 * dorado barre la pantalla, la etapa en curso se anuncia y la barra se llena.
 * Solo dice qué está pasando: no hay nada que hacer mientras.
 */
@Composable
fun LoadingScreen(stage: LoadingStage, percent: Int, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val reduced = Motion.reduced
    val loop = rememberInfiniteTransition(label = "carga")
    val sweep by loop.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(SWEEP_MS, easing = LinearEasing)),
        label = "barrido",
    )
    val pulse by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "latido",
    )
    val shown by animateFloatAsState(percent.coerceIn(0, 100) / 100f, Motion.settle(reduced), label = "progreso")

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF14110A), Color.Black))),
        contentAlignment = Alignment.Center,
    ) {
        // El radar: anillos fijos, uno que se expande y el haz que gira.
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height * 0.42f)
            val radius = size.minDimension * 0.42f
            for (i in 1..RINGS) {
                drawCircle(accent.copy(alpha = 0.10f), radius = radius * i / RINGS, center = center, style = Stroke(width = 1.5f))
            }
            if (!reduced) {
                drawCircle(accent.copy(alpha = 0.35f * (1 - pulse)), radius = radius * pulse, center = center, style = Stroke(width = 3f))
                val a = Math.toRadians(sweep.toDouble())
                val end = Offset(center.x + radius * cos(a).toFloat(), center.y + radius * sin(a).toFloat())
                drawLine(
                    Brush.linearGradient(listOf(accent.copy(alpha = 0f), accent.copy(alpha = 0.7f)), start = center, end = end),
                    start = center,
                    end = end,
                    strokeWidth = 4f,
                )
                drawArc(
                    Brush.sweepGradient(
                        0f to Color.Transparent,
                        (sweep / 360f).coerceIn(0f, 1f) to accent.copy(alpha = 0.18f),
                        center = center,
                    ),
                    startAngle = sweep - TRAIL_DEG,
                    sweepAngle = TRAIL_DEG,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                )
            }
            drawCircle(accent, radius = 6f, center = center)
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.brand).uppercase(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(48.dp))
            // La etapa, sustituida como el subtítulo de una pantalla de carga.
            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    (fadeIn(Motion.settle(reduced)) + slideInVertically(Motion.settle(reduced)) { it / 2 }) togetherWith
                        (fadeOut(Motion.exit(reduced)) + slideOutVertically(Motion.exit(reduced)) { -it / 2 })
                },
                label = "etapa",
            ) { current ->
                Text(
                    text = stringResource(
                        when (current) {
                            LoadingStage.VOICE -> R.string.loading_voice
                            LoadingStage.LISTENING -> R.string.loading_listening
                            LoadingStage.ASSISTANT -> R.string.loading_assistant
                        },
                    ).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    letterSpacing = 3.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.widthIn(max = BAR_WIDTH).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(BAR_HEIGHT)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.08f))
                        .drawBehind {
                            val filled = size.width * shown
                            // El brillo de la barra, más ancho que ella.
                            drawRoundRect(accent.copy(alpha = 0.35f), size = Size(filled, size.height), cornerRadius = CornerRadius(size.height / 2))
                            drawRoundRect(
                                Brush.horizontalGradient(listOf(accent.copy(alpha = 0.6f), accent), endX = filled.coerceAtLeast(1f)),
                                size = Size(filled, size.height * 0.6f),
                                topLeft = Offset(0f, size.height * 0.2f),
                                cornerRadius = CornerRadius(size.height / 2),
                            )
                        },
                )
                Text(
                    text = "${(shown * 100).toInt()}%",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 12.dp).size(width = 48.dp, height = 22.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

private const val SWEEP_MS = 2_600
private const val PULSE_MS = 2_000
private const val RINGS = 4
private const val TRAIL_DEG = 60f
private val BAR_WIDTH: Dp @Composable @ReadOnlyComposable get() = CarTheme.metrics.grown(520.dp)
private val BAR_HEIGHT: Dp @Composable @ReadOnlyComposable get() = CarTheme.metrics.grown(10.dp)
