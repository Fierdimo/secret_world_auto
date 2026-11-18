package com.secretworld.auto.feature.parked

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.secretworld.auto.feature.parked.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.secretworld.auto.core.designsystem.CarButton
import com.secretworld.auto.core.designsystem.CarSecondaryButton
import com.secretworld.auto.core.designsystem.CarTheme
import com.secretworld.auto.core.designsystem.panelInsetsPadding
import com.secretworld.auto.domain.Place

/**
 * La ficha de un lugar, con el vehículo detenido.
 *
 * Lo que la separa de la app móvil: **el mapa no desaparece**. Aquí el panel se
 * ensancha y el mapa centra y resalta el lugar; en el móvil, la ficha sustituye al
 * mapa y el usuario pierde el contexto de dónde está.
 */
@Composable
fun PlaceDetailPanel(
    place: Place,
    /** `null` sin voz: escuchar sería abrir esta misma ficha. */
    onListen: (() -> Unit)?,
    onNavigate: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = CarTheme.dimens
    // Lo que salió de una búsqueda —un restaurante, una gasolinera— no tiene
    // historia: se enseña qué es y dónde, y se ofrece ir.
    val hasStory = place.kind == null && place.summary != null
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.93f))
            .panelInsetsPadding()
            .padding(d.spaceM)
            .verticalScroll(rememberScrollState()),
    ) {
        // De más a menos peso: qué es (pequeño, de acento) y cerrar, en la misma
        // línea; el nombre, a todo el ancho; el detalle, en gris. Con el nombre al
        // lado de "Cerrar", en el hueco pequeño se partía en tres líneas.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = (if (hasStory) place.city else place.kind).orEmpty().uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            CarSecondaryButton(label = stringResource(R.string.close), onClick = onClose, dense = true)
        }
        Spacer(Modifier.height(d.spaceXs))
        Text(place.name, style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        val detail = if (hasStory) {
            stringResource(R.string.listen_minutes, (place.narrationSeconds + 59) / 60)
        } else {
            listOfNotNull(place.address, place.city).joinToString(" · ")
        }
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (place.imageUrl != null) {
            Spacer(Modifier.height(d.spaceS))
            AsyncImage(
                model = place.imageUrl,
                contentDescription = place.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(d.minTouchTarget * 2)
                    .clip(RoundedCornerShape(d.cardCorner)),
            )
        }

        Spacer(Modifier.height(d.spaceS))
        Row(horizontalArrangement = Arrangement.spacedBy(d.spaceXs)) {
            if (hasStory && onListen != null) {
                CarButton(label = stringResource(R.string.listen), onClick = onListen)
                CarSecondaryButton(label = stringResource(R.string.take_me_there), onClick = onNavigate)
            } else {
                CarButton(label = stringResource(R.string.take_me_there), onClick = onNavigate)
            }
        }

        if (place.summary != null) {
            Spacer(Modifier.height(d.spaceS))
            Text(
                text = place.summary!!,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Spacer(Modifier.height(d.spaceL))
    }
}
