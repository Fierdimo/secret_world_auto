package com.secretworld.auto.feature.parked

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.secretworld.auto.core.designsystem.CarButton
import com.secretworld.auto.core.designsystem.CarSecondaryButton
import com.secretworld.auto.core.designsystem.CarTheme
import com.secretworld.auto.core.designsystem.Motion
import com.secretworld.auto.core.designsystem.panelInsetsPadding
import com.secretworld.auto.core.designsystem.pressable
import com.secretworld.auto.domain.Place

/**
 * Lo que un socio sabe de un sitio —opiniones, nota, precio—, con su enlace.
 * Es el hueco de la ficha para monetizar con terceros como Tripadvisor: hoy no
 * hay ninguno conectado y la sección no sale, pero la ficha ya tiene su sitio y
 * su forma, para que conectar uno no sea rehacerla.
 */
data class PartnerInfo(
    /** "Tripadvisor": se enseña tal cual, es su marca y su atribución. */
    val provider: String,
    /** Sobre 5. */
    val rating: Double? = null,
    val reviews: Int? = null,
    /** "€€", "$$$". */
    val priceLevel: String? = null,
    /** Una frase de una opinión, si el socio la deja mostrar. */
    val quote: String? = null,
    /** Abrir la ficha del socio. */
    val onOpen: (() -> Unit)? = null,
)

/**
 * La ficha de un lugar, con el vehículo detenido.
 *
 * De más a menos peso: qué es y cerrar; la foto, si la hay; el nombre; las
 * acciones, del mismo ancho; la historia, recortada con "Ver más" si es larga;
 * y, al final, lo de los socios. Un negocio no tiene historia: se enseña qué es
 * y dónde, y se ofrece ir.
 */
@Composable
fun PlaceDetailPanel(
    place: Place,
    /** `null` sin voz: escuchar sería abrir esta misma ficha. */
    onListen: (() -> Unit)?,
    onNavigate: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Opiniones y datos de socios. Vacío, la sección no sale. */
    partners: List<PartnerInfo> = emptyList(),
) {
    val d = CarTheme.dimens
    val hasStory = place.kind == null && place.summary != null
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .panelInsetsPadding()
            .verticalScroll(rememberScrollState())
            .padding(d.spaceM),
    ) {
        // Qué es (pequeño, de acento) y cerrar, en la misma línea.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                // Un sitio con historia es "lugar de interés": la ciudad que trae
                // Wikipedia puede ser un barrio lejano ("Bocagrande" en el Centro).
                text = (if (hasStory) stringResource(R.string.sight) else place.kind).orEmpty().uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            CarSecondaryButton(label = stringResource(R.string.close), onClick = onClose, dense = true)
        }
        Spacer(Modifier.height(d.spaceS))

        val detail = if (hasStory) {
            // Lo que dura escucharla, solo si se puede escuchar.
            if (onListen != null) stringResource(R.string.listen_minutes, (place.narrationSeconds + 59) / 60) else ""
        } else {
            listOfNotNull(place.address, place.city).joinToString(" · ")
        }
        if (place.imageUrl != null) {
            // La foto manda: ancha y con proporción de foto, no una franja.
            AsyncImage(
                model = place.imageUrl,
                contentDescription = place.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(PHOTO_RATIO)
                    .heightIn(max = d.minTouchTarget * 3)
                    .clip(RoundedCornerShape(d.cardCorner)),
            )
            Spacer(Modifier.height(d.spaceS))
            Title(place.name, detail)
        } else {
            // Sin foto, la inicial junto al nombre, como en la lista: el hueco de
            // una foto vacía era lo primero que se veía.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Initial(place.name)
                Spacer(Modifier.width(d.spaceS))
                Column(Modifier.weight(1f)) { Title(place.name, detail) }
            }
        }

        // Las acciones, del mismo ancho: la principal rellena.
        Spacer(Modifier.height(d.spaceM))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d.spaceXs)) {
            if (hasStory && onListen != null) {
                CarButton(label = stringResource(R.string.listen), onClick = onListen, modifier = Modifier.weight(1f))
                CarSecondaryButton(label = stringResource(R.string.take_me_there), onClick = onNavigate, modifier = Modifier.weight(1f))
            } else {
                CarButton(label = stringResource(R.string.take_me_there), onClick = onNavigate, modifier = Modifier.weight(1f))
            }
        }

        place.summary?.takeIf { it.isNotBlank() }?.let { story ->
            Spacer(Modifier.height(d.spaceM))
            Story(story)
        }

        if (partners.isNotEmpty()) {
            Spacer(Modifier.height(d.spaceM))
            SectionLabel(stringResource(R.string.reviews))
            partners.forEach { partner ->
                PartnerCard(partner)
                Spacer(Modifier.height(d.spaceXs))
            }
        }

        Spacer(Modifier.height(d.spaceL))
    }
}

@Composable
private fun Title(name: String, detail: String) {
    Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    if (detail.isNotBlank()) {
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Initial(name: String) {
    val d = CarTheme.dimens
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Box(
        Modifier
            .size(d.minTouchTarget * 1.1f)
            .clip(RoundedCornerShape(d.cardCorner))
            .background(Brush.linearGradient(listOf(secondary.copy(alpha = 0.55f), accent.copy(alpha = 0.55f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1).uppercase(),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.surface,
        )
    }
}

/**
 * La historia, recortada si es larga, con "Ver más". Entera de golpe empujaba
 * todo lo demás fuera de la pantalla; "Ver más" solo sale si de verdad hay más.
 */
@Composable
private fun Story(text: String) {
    val d = CarTheme.dimens
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = if (expanded) Int.MAX_VALUE else STORY_LINES,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        modifier = Modifier.animateContentSize(Motion.settle(Motion.reduced)),
    )
    if (overflows || expanded) {
        Spacer(Modifier.height(d.spaceXs))
        TextLink(stringResource(if (expanded) R.string.see_less else R.string.see_more)) { expanded = !expanded }
    }
}

@Composable
private fun TextLink(label: String, onClick: () -> Unit) {
    val d = CarTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressable(interaction)
            .clip(RoundedCornerShape(50))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .defaultMinSize(minHeight = d.minTouchTarget * 0.7f)
            .padding(vertical = d.spaceXs),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(CarTheme.dimens.spaceXs))
}

/** Lo de un socio, en su tarjeta y con su nombre: queda claro de quién es. */
@Composable
private fun PartnerCard(partner: PartnerInfo) {
    val d = CarTheme.dimens
    val shape = RoundedCornerShape(d.cardCorner)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape)
            .padding(d.spaceS),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(partner.provider, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            partner.priceLevel?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        val score = listOfNotNull(
            partner.rating?.let { "★ " + String.format(java.util.Locale.getDefault(), "%.1f", it) },
            partner.reviews?.let { stringResource(R.string.reviews_count, it) },
        ).joinToString(" · ")
        if (score.isNotEmpty()) Text(score, style = MaterialTheme.typography.bodyLarge)
        partner.quote?.let {
            Text("“$it”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        partner.onOpen?.let { open -> TextLink(stringResource(R.string.open_in, partner.provider), open) }
    }
}

/** Las líneas de historia que se ven antes de "Ver más". */
private const val STORY_LINES = 6

/** Proporción de la foto de cabecera. */
private const val PHOTO_RATIO = 16f / 9f
