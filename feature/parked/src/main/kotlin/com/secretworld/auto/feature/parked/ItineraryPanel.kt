package com.secretworld.auto.feature.parked

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.secretworld.auto.feature.parked.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.secretworld.auto.core.designsystem.CarButton
import com.secretworld.auto.core.designsystem.CarSecondaryButton
import com.secretworld.auto.core.designsystem.CarTheme
import com.secretworld.auto.domain.Place

/**
 * Un itinerario: paradas en orden, dibujadas también sobre el mapa.
 *
 * **Advertencia honesta**: los itinerarios reales viven detrás de
 * `POST /itineraries`, que exige cuenta, y la vinculación por código todavía no
 * existe. Lo que se muestra aquí se construye con lugares reales del entorno y
 * está etiquetado como demostración. El día que haya cuenta, cambia la fuente y
 * no esta pantalla.
 */
@Composable
fun ItineraryPanel(
    title: String,
    stops: List<Place>,
    onSelect: (Place) -> Unit,
    onStart: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = CarTheme.dimens
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.93f))
            .safeDrawingPadding()
            .padding(d.spaceM),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${stops.size} stops · demo itinerary",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CarSecondaryButton(label = stringResource(R.string.close), onClick = onClose)
        }

        Spacer(Modifier.height(d.spaceS))
        CarButton(label = stringResource(R.string.start_itinerary), onClick = onStart)
        Spacer(Modifier.height(d.spaceS))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(d.spaceXs)) {
            itemsIndexed(stops, key = { _, place -> place.id }) { index, place ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(d.cardCorner))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(d.spaceS),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(d.spaceS),
                ) {
                    StopNumber(index + 1)
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = place.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = place.city ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CarSecondaryButton(label = stringResource(R.string.open), onClick = { onSelect(place) })
                }
            }
        }
    }
}

@Composable
private fun StopNumber(number: Int) {
    Box(
        Modifier
            .size(CarTheme.dimens.minTouchTarget / 2)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$number",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
