package com.secretworld.auto.feature.parked

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.graphicsLayer
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
 * Arriba, **fijo**, un hero: la foto de fondo y, encima, qué es, el nombre,
 * cerrar e ir. Debajo, lo que se lee —la historia, las opiniones—, que es lo
 * único que se desplaza. Antes la foto se llevaba media ficha, "Llévame" era
 * una barra a todo el ancho y, al bajar a leer, las acciones se perdían.
 *
 * Sin foto —un negocio—, el mismo hero sobre el degradado de la marca, con la
 * inicial grande y tenue de fondo.
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
    // Lo que salió de una búsqueda —un restaurante, una gasolinera— no tiene
    // historia: se enseña qué es y dónde, y se ofrece ir.
    val hasStory = place.kind == null && place.summary != null
    val detail = if (hasStory) {
        // Lo que dura escucharla, solo si se puede escuchar.
        if (onListen != null) stringResource(R.string.listen_minutes, (place.narrationSeconds + 59) / 60) else ""
    } else {
        listOfNotNull(place.address, place.city).joinToString(" · ")
    }
    // Un sitio con historia es "lugar de interés": la ciudad que trae Wikipedia
    // puede ser un barrio lejano ("Bocagrande" en el Centro).
    val eyebrow = (if (hasStory) stringResource(R.string.sight) else kindLabel(place.kind)).orEmpty()
    BoxWithConstraints(
        modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .panelInsetsPadding(),
    ) {
        val heroHeight = maxHeight * (if (maxWidth > maxHeight * WIDE_RATIO) HERO_SHARE_WIDE else HERO_SHARE_NARROW)
        Column(Modifier.fillMaxSize()) {
            Hero(
                place = place,
                eyebrow = eyebrow,
                detail = detail,
                onClose = onClose,
                onListen = onListen.takeIf { hasStory },
                onNavigate = onNavigate,
                // Un mínimo, y más si el nombre y la dirección lo piden: con un alto
                // fijo, en el hueco pequeño "Cerrar" pisaba el nombre y "Llévame" se cortaba.
                modifier = Modifier.fillMaxWidth().heightIn(min = heroHeight),
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = d.spaceM, vertical = d.spaceS),
            ) {
                place.summary?.takeIf { it.isNotBlank() }?.let { Story(it) }
                if (partners.isNotEmpty()) {
                    Spacer(Modifier.height(d.spaceM))
                    SectionLabel(stringResource(R.string.reviews))
                    partners.forEach { partner ->
                        PartnerCard(partner)
                        Spacer(Modifier.height(d.spaceXs))
                    }
                }
                Spacer(Modifier.height(d.spaceM))
            }
        }
    }
}

/**
 * La cabecera de la ficha: foto (o degradado con la inicial), y encima, en
 * blanco sobre un velo oscuro, todo lo que identifica el sitio y lo que se
 * puede hacer con él.
 */
@Composable
private fun Hero(
    place: Place,
    eyebrow: String,
    detail: String,
    onClose: () -> Unit,
    onListen: (() -> Unit)?,
    onNavigate: () -> Unit,
    modifier: Modifier,
) {
    val d = CarTheme.dimens
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val white = androidx.compose.ui.graphics.Color.White
    Box(modifier.clip(RoundedCornerShape(bottomStart = d.cardCorner, bottomEnd = d.cardCorner))) {
        if (place.imageUrl != null) {
            AsyncImage(
                model = place.imageUrl,
                contentDescription = place.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Box(
                Modifier.matchParentSize().background(Brush.linearGradient(listOf(secondary, accent))),
                contentAlignment = Alignment.CenterEnd,
            ) {
                // La inicial, grande y tenue: da forma sin fingir una foto.
                Text(
                    text = place.name.take(1).uppercase(),
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = MaterialTheme.typography.displayMedium.fontSize * 3),
                    color = white.copy(alpha = 0.18f),
                    modifier = Modifier.padding(end = d.spaceL),
                )
            }
        }
        // El velo: arriba, para que se lea "Cerrar"; abajo, para el nombre.
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f),
                    0.3f to androidx.compose.ui.graphics.Color.Transparent,
                    0.4f to androidx.compose.ui.graphics.Color.Transparent,
                    1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f),
                ),
            ),
        )
        HeroPill(stringResource(R.string.close), Modifier.align(Alignment.TopEnd).padding(d.spaceS), onClick = onClose)
        // Abajo, sobre lo más oscuro del velo: qué es y el nombre; y las acciones,
        // a su lado si hay sitio, o debajo.
        // Arriba deja el hueco de "Cerrar"; el resto da el alto del hero.
        BoxWithConstraints(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = d.spaceM, end = d.spaceM, bottom = d.spaceM, top = d.minTouchTarget * 0.7f + d.spaceM),
        ) {
            val side = maxWidth > HERO_SIDE_BY_SIDE
            val identity: @Composable (Modifier) -> Unit = { m ->
                Column(m) {
                    if (eyebrow.isNotBlank()) {
                        Text(
                            text = eyebrow.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = white.copy(alpha = 0.9f),
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = place.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = white,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detail.isNotBlank()) {
                        Text(detail, style = MaterialTheme.typography.bodyMedium, color = white.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            val actions: @Composable () -> Unit = {
                Row(horizontalArrangement = Arrangement.spacedBy(d.spaceXs), verticalAlignment = Alignment.CenterVertically) {
                    onListen?.let { ActionPill(stringResource(R.string.listen), filled = false, onClick = it) }
                    ActionPill(stringResource(R.string.take_me_there), filled = true, arrow = true, onClick = onNavigate)
                }
            }
            if (side) {
                Row(verticalAlignment = Alignment.Bottom) {
                    identity(Modifier.weight(1f))
                    Spacer(Modifier.width(d.spaceM))
                    actions()
                }
            } else {
                Column {
                    identity(Modifier)
                    Spacer(Modifier.height(d.spaceS))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { actions() }
                }
            }
        }
    }
}

/** "Cerrar" sobre la foto: oscuro y translúcido, se lee sobre cualquier imagen. */
@Composable
private fun HeroPill(label: String, modifier: Modifier, onClick: () -> Unit) {
    val d = CarTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressable(interaction)
            .clip(RoundedCornerShape(50))
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .defaultMinSize(minHeight = d.minTouchTarget * 0.7f)
            .padding(horizontal = d.spaceM, vertical = d.spaceXs),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = androidx.compose.ui.graphics.Color.White)
    }
}

/** Una acción en píldora sobre la foto: la principal, rellena y con flecha; la otra, con borde blanco. */
@Composable
private fun ActionPill(label: String, filled: Boolean, arrow: Boolean = false, onClick: () -> Unit) {
    val d = CarTheme.dimens
    val accent = MaterialTheme.colorScheme.primary
    val white = androidx.compose.ui.graphics.Color.White
    val ink = if (filled) MaterialTheme.colorScheme.onPrimary else white
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .pressable(interaction)
            .clip(shape)
            .background(if (filled) accent else androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f))
            .border(1.5.dp, if (filled) accent else white, shape)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .defaultMinSize(minHeight = d.minTouchTarget * 0.8f)
            .padding(horizontal = d.spaceM, vertical = d.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (arrow) {
            // La flecha de "ir": un triángulo de navegación, como en los mapas.
            androidx.compose.foundation.Canvas(Modifier.size(d.spaceS * 1.2f)) {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.9f, size.height * 0.1f)
                    lineTo(size.width * 0.1f, size.height * 0.45f)
                    lineTo(size.width * 0.45f, size.height * 0.55f)
                    lineTo(size.width * 0.55f, size.height * 0.9f)
                    close()
                }
                drawPath(p, ink)
            }
            Spacer(Modifier.width(d.spaceXs))
        }
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
    }
}

/**
 * La historia, recortada si es larga. "Ver más" va **encima**, con un chevron
 * que apunta hacia donde se mueve el texto: abajo para desplegarlo, arriba
 * para recogerlo. Debajo del texto quedaba fuera de la vista al desplegarlo y
 * para recogerlo había que bajar hasta el final. Solo sale si hay más.
 */
@Composable
private fun Story(text: String) {
    val d = CarTheme.dimens
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    if (overflows || expanded) {
        Toggle(
            label = stringResource(if (expanded) R.string.see_less else R.string.see_more),
            up = expanded,
        ) { expanded = !expanded }
        Spacer(Modifier.height(d.spaceXs))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = if (expanded) Int.MAX_VALUE else STORY_LINES,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        modifier = Modifier.animateContentSize(Motion.settle(Motion.reduced)),
    )
}

/** "Ver más" o "Ver menos" con su chevron, que gira al cambiar. */
@Composable
private fun Toggle(label: String, up: Boolean, onClick: () -> Unit) {
    val d = CarTheme.dimens
    val accent = MaterialTheme.colorScheme.primary
    val interaction = remember { MutableInteractionSource() }
    val turn by androidx.compose.animation.core.animateFloatAsState(if (up) 180f else 0f, Motion.settle(Motion.reduced), label = "chevron")
    Row(
        Modifier
            .pressable(interaction)
            .clip(RoundedCornerShape(50))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .defaultMinSize(minHeight = d.minTouchTarget * 0.7f)
            .padding(vertical = d.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = accent)
        Spacer(Modifier.width(d.spaceXs))
        // Un chevron dibujado: abajo, y girado media vuelta, arriba.
        androidx.compose.foundation.Canvas(
            Modifier
                .size(d.spaceS)
                .graphicsLayer { rotationZ = turn },
        ) {
            val stroke = size.minDimension * 0.16f
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.15f, size.height * 0.35f)
                lineTo(size.width * 0.5f, size.height * 0.7f)
                lineTo(size.width * 0.85f, size.height * 0.35f)
            }
            drawPath(
                path,
                accent,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                ),
            )
        }
    }
}

/** Un enlace de texto, de acento y sin caja: "Abrir en Tripadvisor". */
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

/** Más ancha que esto respecto a su alto, la ventana cuenta como ancha. */
private const val WIDE_RATIO = 0.9f

/** Lo que se lleva el hero del alto de la ficha: en ancha, algo más. */
private const val HERO_SHARE_WIDE = 0.42f
private const val HERO_SHARE_NARROW = 0.36f

/** Con más ancho que esto, el nombre y las acciones van en la misma línea. */
private val HERO_SIDE_BY_SIDE = 900.dp
