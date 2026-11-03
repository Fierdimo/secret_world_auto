package com.secretworld.auto.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secretworld.auto.core.designsystem.CarButton
import com.secretworld.auto.core.designsystem.CarSecondaryButton
import com.secretworld.auto.core.designsystem.CarTheme
import com.secretworld.auto.core.designsystem.panelInsetsPadding
import com.secretworld.auto.domain.Place

/** Un itinerario guardado, como lo enseña la ventana. */
data class SavedItineraryUi(
    val id: String,
    /** "India Catalina → Convento de Santo Domingo". */
    val title: String,
    /** "5 paradas · 2 horas y 51 · 3 nov". */
    val subtitle: String,
    val stops: List<Place>,
)

/**
 * Los itinerarios guardados, para empezar uno o verlo antes. Se elige tocando o
 * de viva voz: "el segundo", "empieza el del castillo", "borra el primero"; por
 * eso cada uno lleva su número.
 */
@Composable
fun ItineraryLibraryView(
    items: List<SavedItineraryUi>,
    voice: VoiceUi,
    onOpen: (String) -> Unit,
    onStart: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = CarTheme.dimens
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .panelInsetsPadding()
            .padding(d.spaceM),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(d.spaceS))
            VoiceStatusChip(voice.status, labelled = false)
            Spacer(Modifier.weight(1f))
            CarSecondaryButton(label = stringResource(R.string.close_view), onClick = onClose, compact = true)
        }
        Text(
            text = voice.answer ?: voice.heard?.let { "“$it”" } ?: stringResource(R.string.library_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(d.spaceM))
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.library_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(CARD_MIN),
            horizontalArrangement = Arrangement.spacedBy(d.spaceM),
            verticalArrangement = Arrangement.spacedBy(d.spaceM),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                SavedCard(index + 1, item, onOpen = { onOpen(item.id) }, onStart = { onStart(item.id) }, onDelete = { onDelete(item.id) })
            }
        }
    }
}

@Composable
private fun SavedCard(number: Int, item: SavedItineraryUi, onOpen: () -> Unit, onStart: () -> Unit, onDelete: () -> Unit) {
    val d = CarTheme.dimens
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .clip(RoundedCornerShape(d.cardCorner))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(d.cardCorner))
            .clickable(onClick = onOpen)
            .padding(d.spaceS),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // El número es el que se dice: "el segundo".
            Box(Modifier.size(BADGE).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                Text("$number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(d.spaceS))
            Row(horizontalArrangement = Arrangement.spacedBy(d.spaceXs)) {
                item.stops.take(THUMBS).forEach { PlaceThumb(it, THUMB) }
            }
        }
        Spacer(Modifier.height(d.spaceS))
        Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(d.spaceS))
        Row(horizontalArrangement = Arrangement.spacedBy(d.spaceS), verticalAlignment = Alignment.CenterVertically) {
            CarButton(label = stringResource(R.string.library_start), onClick = onStart, dense = true)
            CarSecondaryButton(label = stringResource(R.string.library_view), onClick = onOpen, dense = true)
            Spacer(Modifier.weight(1f))
            CarSecondaryButton(label = stringResource(R.string.library_delete), onClick = onDelete, compact = true)
        }
    }
}

private val CARD_MIN = 320.dp
private val BADGE = 32.dp
private val THUMB = 44.dp
private const val THUMBS = 5
