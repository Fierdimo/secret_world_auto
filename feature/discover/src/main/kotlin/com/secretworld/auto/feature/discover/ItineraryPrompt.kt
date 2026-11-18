package com.secretworld.auto.feature.discover

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
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

/**
 * Una pregunta del itinerario con sus respuestas, para contestarla tocando.
 *
 * Tres niveles, de más a menos peso: las [choices] —lo que se pregunta, en
 * rejilla regular—, la acción [primary] y la [secondary] al pie, y [dismiss],
 * salir, como texto a la izquierda.
 */
data class PromptUi(
    /** Contexto, pequeño y encima: "Nuevo itinerario". */
    val eyebrow: String,
    val question: String,
    val choices: List<PromptOption> = emptyList(),
    val primary: PromptOption? = null,
    val secondary: PromptOption? = null,
    val dismiss: PromptOption? = null,
    /** Abajo y en lo justo, para no tapar el itinerario que se propone. */
    val atBottom: Boolean = false,
)

data class PromptOption(val label: String, val onClick: () -> Unit)

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
            // Sobre lo que queda de mapa, a la derecha del panel. Con la ventana
            // apilada, abajo sobre el panel: arriba, en el mapa, no cabían todas
            // las respuestas y la barra de iconos las tapaba.
            .padding(start = if (CarTheme.metrics.stacked) 0.dp else CarTheme.metrics.widthDp * panelShare),
        contentAlignment = if (last?.atBottom == true || CarTheme.metrics.stacked) Alignment.BottomCenter else Alignment.Center,
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
    // Con el itinerario a la vista, la tarjeta se queda en lo justo: la línea de
    // tiempo y el mapa tienen que seguir viéndose.
    val slim = prompt.atBottom
    Column(
        Modifier
            .padding(if (slim) d.spaceXs else d.spaceM)
            .widthIn(max = CarTheme.metrics.grown(CARD_WIDTH))
            .shadow(d.spaceS, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, accent.copy(alpha = 0.3f), shape)
            .padding(if (slim) d.spaceS else d.spaceM),
    ) {
        Text(
            text = prompt.eyebrow.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
        )
        Spacer(Modifier.height(d.spaceXs / 2))
        Text(
            text = prompt.question,
            style = if (slim) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (prompt.choices.isNotEmpty()) {
            Spacer(Modifier.height(d.spaceS))
            ChoiceGrid(prompt.choices)
        }
        if (prompt.primary != null || prompt.secondary != null || prompt.dismiss != null) {
            Spacer(Modifier.height(if (slim) d.spaceXs else d.spaceS))
            // Al pie y en orden de peso: salir a la izquierda, como texto; lo
            // demás a la derecha, la acción principal la última y rellena.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                prompt.dismiss?.let { TextAction(it) }
                Spacer(Modifier.weight(1f))
                prompt.secondary?.let {
                    PillButton(it, filled = false)
                    Spacer(Modifier.width(d.spaceXs))
                }
                prompt.primary?.let { PillButton(it, filled = true) }
            }
        }
    }
}

/**
 * Las respuestas en una rejilla regular: todas del mismo ancho y las filas
 * completas, con tantas columnas como quepan. Antes se apilaban como cabían
 * —dos, dos y una— y no se leían como una lista de opciones.
 */
@Composable
private fun ChoiceGrid(choices: List<PromptOption>) {
    val d = CarTheme.dimens
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val minWidth = d.minTouchTarget * CHOICE_MIN_WIDTH
        val fits = ((maxWidth + d.spaceXs) / (minWidth + d.spaceXs)).toInt().coerceIn(1, choices.size)
        // Filas parejas: con cinco y sitio para cuatro, tres y dos; no cuatro y una.
        val rows = (choices.size + fits - 1) / fits
        val columns = (choices.size + rows - 1) / rows
        Column(verticalArrangement = Arrangement.spacedBy(d.spaceXs)) {
            choices.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d.spaceXs)) {
                    row.forEach { ChoiceButton(it, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ChoiceButton(option: PromptOption, modifier: Modifier) {
    val d = CarTheme.dimens
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(d.cardCorner * 0.75f)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressable(interaction)
            .clip(shape)
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.45f), shape)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = option.onClick)
            .defaultMinSize(minHeight = d.minTouchTarget * 0.8f)
            .padding(horizontal = d.spaceXs, vertical = d.spaceXs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = option.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun PillButton(option: PromptOption, filled: Boolean) {
    val d = CarTheme.dimens
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressable(interaction)
            .clip(shape)
            .background(if (filled) accent else MaterialTheme.colorScheme.surface)
            .border(1.5.dp, accent, shape)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = option.onClick)
            .defaultMinSize(minWidth = d.minTouchTarget * 1.3f, minHeight = d.minTouchTarget * 0.75f)
            .padding(horizontal = d.spaceM, vertical = d.spaceXs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = option.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (filled) MaterialTheme.colorScheme.onPrimary else accent,
            maxLines = 1,
        )
    }
}

/** Salir: sin caja, en gris. Se encuentra, pero no compite con lo demás. */
@Composable
private fun TextAction(option: PromptOption) {
    val d = CarTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressable(interaction)
            .clip(RoundedCornerShape(50))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = option.onClick)
            .defaultMinSize(minHeight = d.minTouchTarget * 0.75f)
            .padding(horizontal = d.spaceS, vertical = d.spaceXs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = option.label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Lo mínimo que mide una respuesta, respecto al objetivo táctil: así salen las columnas. */
private const val CHOICE_MIN_WIDTH = 1.5f

/** Ancho de la tarjeta en el móvil; crece con la pantalla. */
private val CARD_WIDTH = 440.dp
