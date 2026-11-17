package com.secretworld.auto.feature.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.secretworld.auto.core.designsystem.CarTheme
import com.secretworld.auto.core.designsystem.Motion
import com.secretworld.auto.core.designsystem.pressable
import kotlinx.coroutines.delay

/** Lo que hay en la barra de iconos de arriba a la derecha. */
enum class MenuIcon(internal val glyph: Glyph) { ITINERARIES(Glyph.ROUTE), SETTINGS(Glyph.SETTINGS) }

data class MenuEntry(val icon: MenuIcon, val label: String, val onActivate: () -> Unit)

/** Qué icono está desplegado y dónde está la barra, para recogerla al tocar fuera. */
class MenuRailState {
    var open by mutableStateOf<MenuIcon?>(null)
    internal var bounds: Rect = Rect.Zero
}

@Composable
fun rememberMenuRailState(): MenuRailState = remember { MenuRailState() }

/**
 * Para la pantalla entera: un toque fuera de la barra la recoge. Solo mira, no
 * se queda con el toque: lo tocado —un sitio, el mapa— responde igual.
 */
fun Modifier.collapseMenuOnOutsideTap(state: MenuRailState): Modifier = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (state.open != null && !state.bounds.contains(down.position)) state.open = null
    }
}

/**
 * La barra de iconos sobre el mapa. Un toque despliega el icono con su nombre
 * —se sabe qué es sin adivinarlo—; un segundo toque, ya desplegado, lo activa.
 * Si no se toca nada, se recoge solo.
 */
@Composable
fun MenuRail(entries: List<MenuEntry>, state: MenuRailState, modifier: Modifier = Modifier) {
    val d = CarTheme.dimens
    var open by state::open
    LaunchedEffect(open) {
        if (open == null) return@LaunchedEffect
        delay(COLLAPSE_MS)
        open = null
    }
    // Descendente, pegada al borde derecho: el nombre se despliega hacia dentro.
    Column(modifier.onGloballyPositioned { state.bounds = it.boundsInRoot() }, verticalArrangement = Arrangement.spacedBy(d.spaceS), horizontalAlignment = Alignment.End) {
        entries.forEach { entry ->
            val expanded = open == entry.icon
            MenuButton(entry, expanded) {
                if (expanded) {
                    open = null
                    entry.onActivate()
                } else {
                    open = entry.icon
                }
            }
        }
    }
}

@Composable
private fun MenuButton(entry: MenuEntry, expanded: Boolean, onClick: () -> Unit) {
    val d = CarTheme.dimens
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .pressable(interaction)
            .clip(shape)
            // Del color de los paneles: oscuro con el tema oscuro, claro con el claro.
            .background(if (expanded) accent else MaterialTheme.colorScheme.surface.copy(alpha = 0.84f))
            .border(1.dp, accent.copy(alpha = if (expanded) 1f else 0.4f), shape)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(start = if (expanded) d.spaceM else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = expandHorizontally(Motion.settle(Motion.reduced), expandFrom = Alignment.End) + fadeIn(Motion.settle(Motion.reduced)),
            exit = shrinkHorizontally(Motion.settle(Motion.reduced), shrinkTowards = Alignment.End) + fadeOut(Motion.exit(Motion.reduced)),
        ) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
            )
        }
        Box(Modifier.size(SIZE).clip(CircleShape), contentAlignment = Alignment.Center) {
            GlyphIcon(entry.icon.glyph, if (expanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, SIZE * 0.42f)
        }
    }
}

private val SIZE = 52.dp
private const val COLLAPSE_MS = 3_500L
