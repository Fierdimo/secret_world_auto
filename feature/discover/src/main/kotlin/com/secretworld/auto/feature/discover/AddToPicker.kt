package com.secretworld.auto.feature.discover

import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.ReadOnlyComposable
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secretworld.auto.core.designsystem.CarTheme
import com.secretworld.auto.core.designsystem.Motion
import com.secretworld.auto.core.designsystem.entering
import com.secretworld.auto.core.designsystem.pressable
import com.secretworld.auto.core.designsystem.rememberListEntrance
import com.secretworld.auto.domain.Phrasebook
import com.secretworld.auto.domain.Place

/**
 * A qué itinerario va el sitio tocado: a uno de los guardados o a uno nuevo.
 * Sale solo cuando no hay ninguno abierto; con uno abierto, el sitio va a ese.
 * Tocar fuera lo cierra sin añadir nada.
 */
@Composable
fun BoxScope.AddToPicker(
    place: Place?,
    saved: List<SavedItineraryUi>,
    onPick: (Int) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val reduced = Motion.reduced
    AnimatedVisibility(
        visible = place != null,
        enter = fadeIn(Motion.settle(reduced)),
        exit = fadeOut(Motion.exit(reduced)),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = place != null,
                enter = scaleIn(Motion.reward(reduced), initialScale = 0.9f) + fadeIn(Motion.settle(reduced)),
                exit = scaleOut(Motion.exit(reduced), targetScale = 0.95f) + fadeOut(Motion.exit(reduced)),
            ) {
                PickerCard(place, saved, onPick, onNew)
            }
        }
    }
}

@Composable
private fun PickerCard(place: Place?, saved: List<SavedItineraryUi>, onPick: (Int) -> Unit, onNew: () -> Unit) {
    val d = CarTheme.dimens
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(d.cardCorner)
    Column(
        Modifier
            .widthIn(max = CARD_WIDTH)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, accent.copy(alpha = 0.45f), shape)
            // El toque dentro no cierra.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(d.spaceM),
    ) {
        Text(
            text = stringResource(R.string.add_to_title, place?.let { Phrasebook.shortName(it) }.orEmpty()),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(d.spaceS))
        val entrance = rememberListEntrance(place?.id)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = LIST_MAX), verticalArrangement = Arrangement.spacedBy(d.spaceXs)) {
            itemsIndexed(saved, key = { _, it -> it.id }) { index, item ->
                val interaction = remember { MutableInteractionSource() }
                Row(
                    Modifier
                        .entering(entrance, index)
                        .pressable(interaction, Motion.PRESSED_ROW_SCALE)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(d.cardCorner * 0.75f))
                        .clickable(interactionSource = interaction, indication = LocalIndication.current) { onPick(index) }
                        .padding(d.spaceXs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(accent.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                        Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(d.spaceS))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${item.count} · ${item.duration}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(d.spaceS))
        val interaction = remember { MutableInteractionSource() }
        Row(
            Modifier
                .entering(entrance, saved.size)
                .pressable(interaction, Motion.PRESSED_ROW_SCALE)
                .fillMaxWidth()
                .clip(RoundedCornerShape(d.cardCorner * 0.75f))
                .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(d.cardCorner * 0.75f))
                .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onNew)
                .padding(d.spaceS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("+", style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(d.spaceS))
            Text(stringResource(R.string.add_to_new), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

private val CARD_WIDTH: Dp @Composable @ReadOnlyComposable get() = CarTheme.metrics.grown(460.dp)
private val LIST_MAX: Dp @Composable @ReadOnlyComposable get() = CarTheme.metrics.grown(260.dp)
