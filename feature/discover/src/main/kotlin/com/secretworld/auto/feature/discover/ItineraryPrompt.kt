package com.secretworld.auto.feature.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.secretworld.auto.core.designsystem.CarTheme
import com.secretworld.auto.core.designsystem.Motion
import com.secretworld.auto.core.designsystem.pressable

/** Una pregunta del itinerario con sus respuestas, para contestarla tocando. */
data class PromptUi(
    val question: String,
    val options: List<PromptOption>,
    /** Abajo, para no tapar lo que hay en el mapa: el itinerario propuesto. */
    val atBottom: Boolean = false,
)

/** Una respuesta: la principal va rellena; las demás, con borde. */
data class PromptOption(val label: String, val primary: Boolean = false, val onClick: () -> Unit)

/**
 * Lo que pregunta el itinerario —cuánto tiempo, qué le gusta, si empezamos—,
 * **sobre el mapa** y con todas las respuestas a la vista. En la caja de la
 * izquierda las opciones quedaban apretadas debajo de la pregunta, y sin voz
 * eran la única forma de seguir.
 *
 * Va centrada en lo que queda de mapa a la derecha del panel ([panelShare]); en
 * una ventana estrecha, sin mapa, en el centro.
 */
@Composable
fun BoxScope.ItineraryPrompt(prompt: PromptUi?, panelShare: Float) {
    val reduced = Motion.reduced
    // Lo último que se enseñó, para que la tarjeta no se vacíe mientras sale.
    var last by remember { mutableStateOf<PromptUi?>(null) }
    if (prompt != null) last = prompt
    Box(
        Modifier
            .fillMaxSize()
            .padding(start = CarTheme.metrics.widthDp * panelShare),
        contentAlignment = if (last?.atBottom == true) Alignment.BottomCenter else Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = prompt != null,
            enter = scaleIn(Motion.reward(reduced), initialScale = 0.9f) + fadeIn(Motion.settle(reduced)),
            exit = scaleOut(Motion.exit(reduced), targetScale = 0.95f) + fadeOut(Motion.exit(reduced)),
        ) {
            last?.let { PromptCard(it) }
        }
    }
}

@Composable
private fun PromptCard(prompt: PromptUi) {
    val d = CarTheme.dimens
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(d.cardCorner)
    Column(
        Modifier
            .padding(d.spaceM)
            .widthIn(max = CarTheme.metrics.grown(CARD_WIDTH))
            .shadow(d.spaceS, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, accent.copy(alpha = 0.45f), shape)
            .padding(d.spaceM),
    ) {
        Text(
            text = prompt.question,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(d.spaceS))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(d.spaceXs),
            verticalArrangement = Arrangement.spacedBy(d.spaceXs),
        ) {
            prompt.options.forEach { option -> OptionButton(option) }
        }
    }
}

@Composable
private fun OptionButton(option: PromptOption) {
    val d = CarTheme.dimens
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressable(interaction)
            .clip(shape)
            .background(if (option.primary) accent else MaterialTheme.colorScheme.surface)
            .border(1.5.dp, accent, shape)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = option.onClick)
            .defaultMinSize(minWidth = d.minTouchTarget * 1.2f, minHeight = d.minTouchTarget * 0.75f)
            .padding(horizontal = d.spaceS, vertical = d.spaceXs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = option.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (option.primary) MaterialTheme.colorScheme.onPrimary else accent,
            maxLines = 1,
        )
    }
}

/** Ancho de la tarjeta en el móvil; crece con la pantalla. */
private val CARD_WIDTH = 440.dp
