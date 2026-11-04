package com.secretworld.auto.core.designsystem

import android.animation.ValueAnimator
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
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
fun Modifier.pressable(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) Motion.PRESSED_SCALE else 1f,
        animationSpec = Motion.tactile(Motion.reduced),
        label = "pulsado",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
