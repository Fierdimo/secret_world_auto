package com.secretworld.auto.core.designsystem

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * El movimiento de toda la app: el mapa es el mundo y lo de encima —paneles,
 * barra, marcadores— el HUD de un videojuego, que entra y sale siempre con la
 * misma familia de muelles. Así se siente de una pieza.
 *
 * - **Encaje**: paneles, pestañas, selección. Sin rebote: llega y se queda.
 * - **Táctil**: un botón al soltarlo. Firme, apenas rebota.
 * - **Recompensa**: lo que sale bien —añadir, guardar—. Con rebote, y solo ahí.
 *
 * Con las animaciones del sistema quitadas (accesibilidad, ahorro de batería),
 * todo salta a su sitio: [reduced].
 */
object Motion {

    /** Paneles, pestañas, selección. */
    fun <T> settle(reduced: Boolean = false): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)

    /** Un botón que se suelta. */
    fun <T> tactile(reduced: Boolean = false): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.85f)

    /** Lo que sale bien. */
    fun <T> reward(reduced: Boolean = false): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy)

    /** Lo que se va: más corto y más simple que lo que entra. */
    fun <T> exit(reduced: Boolean = false): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(EXIT_MS)

    /** Entre un elemento y el siguiente de una lista que entra escalonada. */
    const val STAGGER_MS = 35L

    /** Hasta cuántos elementos se escalonan; los demás entran con el último. */
    const val STAGGER_MAX = 8

    /** Cuánto se hunde un botón al pulsarlo. */
    const val PRESSED_SCALE = 0.92f

    /** Cuánto se hunde una fila: es ancha, y con la escala de un botón se veía saltar. */
    const val PRESSED_ROW_SCALE = 0.97f

    /** Lo que tarda en entrar cada elemento de una lista escalonada. */
    const val ENTER_MS = 320

    private const val EXIT_MS = 140

    /** Si el sistema tiene las animaciones quitadas. */
    val reduced: Boolean
        @Composable @ReadOnlyComposable get() = LocalReducedMotion.current
}

/** Si las animaciones están quitadas en el sistema; lo fija el tema al arrancar. */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Lo que dice el sistema: "Quitar animaciones", la escala a 0 o el ahorro de batería. */
fun systemReducesMotion(): Boolean = !ValueAnimator.areAnimatorsEnabled()

/**
 * Se hunde un poco al pulsar y vuelve con un muelle táctil. La escala va en la
 * capa de dibujo: no rehace el layout en cada fotograma.
 */
@Composable
fun Modifier.pressable(interaction: MutableInteractionSource, pressedScale: Float = Motion.PRESSED_SCALE): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = Motion.tactile(Motion.reduced),
        label = "pulsado",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * La entrada escalonada de una lista: un solo reloj por aparición, y cada fila
 * lee su tramo al dibujarse —ni recompone ni rehace el layout—. Lo que entra
 * después con el desplazamiento ya encuentra el reloj parado y sale entero.
 */
@Stable
class ListEntrance internal constructor(private val clock: Animatable<Float, AnimationVector1D>) {
    internal fun progress(index: Int): Float {
        val start = index.coerceAtMost(Motion.STAGGER_MAX) * Motion.STAGGER_MS
        return FastOutSlowInEasing.transform(((clock.value - start) / Motion.ENTER_MS).coerceIn(0f, 1f))
    }
}

/** Un reloj nuevo cada vez que cambia alguna de [keys]: la lista vuelve a entrar. */
@Composable
fun rememberListEntrance(vararg keys: Any?): ListEntrance {
    val reduced = Motion.reduced
    val clock = remember(*keys) { Animatable(if (reduced) ENTRANCE_END else 0f) }
    LaunchedEffect(clock) { clock.animateTo(ENTRANCE_END, tween(ENTRANCE_END.toInt(), easing = LinearEasing)) }
    return remember(clock) { ListEntrance(clock) }
}

/** La fila [index] de [entrance]: aparece y sube un poco hasta su sitio. */
fun Modifier.entering(entrance: ListEntrance, index: Int): Modifier = graphicsLayer {
    val t = entrance.progress(index)
    alpha = t
    translationY = (1 - t) * ENTER_RISE_DP * density
}

private const val ENTRANCE_END = (Motion.STAGGER_MAX * Motion.STAGGER_MS + Motion.ENTER_MS).toFloat()
private const val ENTER_RISE_DP = 14f
