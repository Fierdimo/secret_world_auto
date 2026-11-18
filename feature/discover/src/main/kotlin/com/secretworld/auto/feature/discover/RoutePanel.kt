package com.secretworld.auto.feature.discover

import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.secretworld.auto.feature.discover.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.getValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.secretworld.auto.core.designsystem.CarButton
import com.secretworld.auto.core.designsystem.CarSecondaryButton
import com.secretworld.auto.core.designsystem.CarTheme
import com.secretworld.auto.core.designsystem.panelInsetsPadding
import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.DrivingMode
import com.secretworld.auto.domain.Place

/**
 * El panel acoplado al mapa.
 *
 * Aquí está la diferencia entre una app de móvil y una de coche: la información
 * **no tapa el mapa**, se pone a su lado. El mapa no es una pantalla a la que se
 * entra, es el fondo permanente sobre el que ocurre todo.
 *
 * La densidad cambia con el estado del vehículo, pero **no cambia la pantalla**:
 * en marcha se muestran menos elementos y más grandes; detenido, más y con scroll.
 */
@Composable
fun RoutePanel(
    places: List<Place>,
    origin: Coordinates,
    mode: DrivingMode,
    city: String?,
    nowPlaying: NowPlaying?,
    voice: VoiceUi,
    offline: Boolean,
    onSelect: (Place) -> Unit,
    onListen: (Place) -> Unit,
    onNavigate: (Place) -> Unit,
    modifier: Modifier = Modifier,
    navigation: NavigationUi? = null,
    onStopNavigation: () -> Unit = {},
    notices: List<PermissionNotice> = emptyList(),
    onStopNarration: () -> Unit = {},
    /** Lo que se buscó, si se buscó algo: sustituye a la lista de siempre. */
    search: SearchUi? = null,
    onClearSearch: () -> Unit = {},
    /** Búsquedas de un toque, para no tener que hablar. */
    shortcuts: List<SearchShortcut> = emptyList(),
) {
    val d = CarTheme.dimens
    val driving = mode == DrivingMode.DRIVING
    // Lo buscado manda sobre lo cercano: si se pidieron restaurantes, la lista
    // son los restaurantes, no los monumentos de siempre.
    val shown = search?.places ?: places
    // Con ruta, el panel es **solo** la ruta: una tarjeta arriba que deja el
    // resto de la pantalla al mapa. La lista de sitios vuelve al terminar.
    val navigating = navigation != null

    Column(
        modifier = modifier
            .then(if (navigating) Modifier else Modifier.fillMaxHeight())
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.93f))
            .panelInsetsPadding()
            // Arriba, lo justo para despegar el título de la barra de estado: cada
            // dp de margen decorativo aquí es un dp que pierde la lista, que es lo
            // único que el conductor va a leer.
            .padding(horizontal = d.spaceM, vertical = d.spaceXs),
    ) {
        PanelHeader(city = city, offline = offline, status = voice.status)

        // Lo que falta para funcionar entero va arriba, antes que nada: sin
        // micrófono la app no oye, y sin botón de hablar sería imposible
        // saberlo.
        notices.forEach {
            Spacer(Modifier.height(d.spaceS))
            PermissionCard(it)
        }

        // La ruta activa va lo primero: mientras se navega es lo único que se
        // mira de reojo, y la lista de lugares pasa a segundo plano.
        if (navigation != null) {
            Spacer(Modifier.height(d.spaceS))
            NavigationCard(navigation, onStopNavigation)
            if (voice.visible) {
                Spacer(Modifier.height(d.spaceS))
                VoiceCard(voice)
            }
            if (nowPlaying != null) {
                Spacer(Modifier.height(d.spaceS))
                NowPlayingCard(nowPlaying, onStopNarration)
            }
            Spacer(Modifier.height(d.spaceXs))
            return@Column
        }

        if (!driving && search == null && shortcuts.isNotEmpty()) {
            Spacer(Modifier.height(d.spaceS))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(d.spaceXs),
            ) {
                shortcuts.forEach { CarSecondaryButton(label = it.label, onClick = it.onClick, compact = true) }
            }
        }

        if (search != null) {
            Spacer(Modifier.height(d.spaceS))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = search.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                CarSecondaryButton(label = stringResource(R.string.search_clear), onClick = onClearSearch, compact = true)
            }
        } else if (CarTheme.metrics.heightDp >= COMPACT_HEIGHT) {
            Spacer(Modifier.height(d.spaceM))
            Text(
                text = stringResource(if (driving) R.string.section_driving else R.string.section_parked),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(Modifier.height(d.spaceS))

        if (shown.isEmpty()) {
            // Preferir el silencio al relleno vale también para la pantalla.
            Text(
                text = stringResource(R.string.nothing_around),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        // Al cambiar de modo la lista conserva el desplazamiento anterior y corta
        // el primer elemento: en marcha eso significa esconder justo el lugar que
        // viene. Se vuelve arriba en cada cambio.
        val listState = rememberLazyListState()
        LaunchedEffect(mode, search) { listState.scrollToItem(0) }

        // Cuántos caben no lo decide una constante ni el alto de la pantalla: lo
        // decide el hueco que de verdad ha sobrado tras componer cabecera, lo que
        // suena y el título de sección. Se mide aquí y se pregunta a CarMetrics.
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            // En marcha, el límite no es el espacio sino la atención: solo lo que
            // se lee de un vistazo, sin desplazar. Parado hay desplazamiento, así
            // que recortar la lista a "lo que cabe" solo consigue esconder lugares
            // y dejar medio panel vacío.
            val footerReserve = if (driving) d.spaceM else 0.dp
            val limit = if (driving) {
                CarTheme.metrics.itemsIn(maxHeight - footerReserve, driving = true)
            } else {
                shown.size
            }

            Column(Modifier.fillMaxHeight()) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(d.spaceS),
                    userScrollEnabled = !driving,
                    // Parado ocupa todo el hueco —una tarjeta cortada abajo es lo
                    // que indica que hay más—; en marcha solo lo que necesita, para
                    // que el pie de "hay más" quede pegado a la lista.
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = !driving),
                ) {
                    items(shown.take(limit), key = { it.id }) { place ->
                        PlaceEntry(
                            place = place,
                            distance = formatDistance(origin.distanceTo(place.position)),
                            // Se pregunta si cabe, no se supone por el tamaño de
                            // pantalla. Donde no cabe, el detalle se abre tocando la
                            // tarjeta entera, que es un objetivo mucho mayor.
                            showDetails = !driving &&
                                CarTheme.metrics.fitsActions(3, expanded = false),
                            // Lo que salió de una búsqueda no tiene historia que oír.
                            hasStory = place.kind == null && place.summary != null,
                            showNavigate = CarTheme.metrics.fitsActions(2, expanded = false),
                            dense = !driving,
                            onSelect = { onSelect(place) },
                            onListen = { onListen(place) },
                            onNavigate = { onNavigate(place) },
                        )
                    }
                }

                if (shown.size > limit) {
                    Spacer(Modifier.height(d.spaceXs))
                    Text(
                        text = stringResource(
                            if (driving) R.string.more_when_stopped else R.string.more_nearby,
                            shown.size - limit,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Lo que suena va **al pie**, no sobre la lista. Arriba empujaba las
        // tarjetas justo el alto de una: donde el usuario acababa de ver el Listen
        // del segundo sitio quedaba el del primero, y el siguiente toque reiniciaba
        // el que ya sonaba en vez de abrir el nuevo. Al pie, la lista no se mueve.
        if (voice.visible) {
            Spacer(Modifier.height(d.spaceS))
            VoiceCard(voice)
        }

        if (nowPlaying != null) {
            Spacer(Modifier.height(d.spaceS))
            NowPlayingCard(nowPlaying, onStopNarration)
        }
    }
}

/** La ruta en curso, tal como se enseña: a dónde, cuánto queda y qué hacer ahora. */
data class NavigationUi(
    val destination: String,
    /** "1.2 km", "300 m". */
    val remaining: String,
    /** "6 min". */
    val eta: String,
    val arrived: Boolean = false,
)

@Composable
internal fun NavigationCard(state: NavigationUi, onStop: () -> Unit) {
    val d = CarTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.cardCorner))
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f))
            .padding(d.spaceS),
    ) {
        // El destino una vez, en grande; encima, solo la llegada.
        if (state.arrived) {
            Text(
                text = stringResource(R.string.nav_arrived),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Las maniobras no son nuestras: las da el navegador instalado. Aquí,
        // solo adónde se va y cuánto falta.
        Text(
            text = state.destination,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${state.remaining} · ${state.eta}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Terminar la ruta va en su propia línea, centrado y en el color de
        // alerta: al lado de los datos se confundía con ellos y quitaba sitio
        // a la distancia, que es lo que se lee de reojo.
        Spacer(Modifier.height(d.spaceS))
        Button(
            onClick = onStop,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .defaultMinSize(minWidth = d.actionMinWidth * END_BUTTON_WIDTH, minHeight = d.minTouchTarget * END_BUTTON_HEIGHT),
            shape = RoundedCornerShape(d.cardCorner),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text(
                text = stringResource(R.string.nav_end),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** El botón de terminar: más ancho que una acción, algo menos alto que un objetivo de marcha. */
private const val END_BUTTON_WIDTH = 1.6f
private const val END_BUTTON_HEIGHT = 0.8f

/** Lo que suena ahora, con progreso real. Sin esto la app parece muda aunque hable. */
data class NowPlaying(
    val placeName: String,
    val positionMs: Int,
    val durationMs: Int,
    val preparing: Boolean = false,
    /** Si se puede cortar. No lo es la instalación de modelos, que usa la misma tarjeta. */
    val stoppable: Boolean = true,
)

@Composable
internal fun NowPlayingCard(state: NowPlaying, onStop: () -> Unit) {
    val d = CarTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.cardCorner))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .padding(d.spaceS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(if (state.preparing) R.string.preparing else R.string.now_playing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = state.placeName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!state.preparing && state.durationMs > 0) {
                Spacer(Modifier.height(d.spaceXs))
                LinearProgressIndicator(
                    progress = { state.positionMs.toFloat() / state.durationMs },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                )
                Spacer(Modifier.height(d.spaceXs))
                Text(
                    text = "${formatTime(state.positionMs)} / ${formatTime(state.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Mientras la app habla no escucha —sin cancelación de eco se oiría a
        // sí misma—, así que "Sícret, basta" no llega. Hasta que se pueda
        // interrumpir hablando, se corta aquí.
        if (state.stoppable) {
            Spacer(Modifier.width(d.spaceS))
            CarSecondaryButton(label = stringResource(R.string.stop_narration), onClick = onStop, dense = true)
        }
    }
}

/**
 * En una ventana baja —un móvil apaisado, media pantalla— el rótulo de marca se
 * come un cuarto del alto disponible y desplaza fuera los lugares, que es lo
 * único que el usuario necesita. Ahí se reduce a una línea: la marca es de la
 * app, el sitio es del viaje.
 */
@Composable
private fun PanelHeader(
    city: String?,
    offline: Boolean,
    status: VoiceStatus,
) {
    val place = city ?: stringResource(R.string.finding_where)
    if (CarTheme.metrics.heightDp < COMPACT_HEIGHT) {
        // Con el alto justo, la marca sobra: la app ya está en pantalla y repetir
        // su nombre costaba una línea entera —una tarjeta de lugar menos—. Lo que
        // el conductor necesita saber es dónde está.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = place,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (offline) {
                Text(
                    text = "  ·  " + stringResource(R.string.offline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            // El título se lleva lo que sobre; el estado, solo lo que ocupa.
            VoiceStatusChip(status)
        }
        return
    }

    // Sin botón de hablar: se habla con la palabra de activación, y el
    // micrófono está abierto siempre que hay permiso.
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.brand), style = MaterialTheme.typography.displayMedium, modifier = Modifier.weight(1f))
            VoiceStatusChip(status)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = place,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (offline) {
                Text(
                    text = "  ·  " + stringResource(R.string.offline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

/**
 * Lo que ve el conductor del asistente de voz.
 *
 * [heard] y [answer] van juntos a propósito: enseñar solo la respuesta esconde el
 * error más frecuente del reconocimiento, que es haber entendido otra cosa. Ver
 * lo que se oyó es lo que permite al usuario corregirse.
 */
data class VoiceUi(
    /** La palabra de activación del idioma, para enseñarla en el botón. */
    val wakeWord: String = "Hey Secret",
    val listening: Boolean = false,
    /** El micrófono está abierto en permanencia, esperando la palabra clave. */
    val handsFree: Boolean = false,
    val heard: String? = null,
    val answer: String? = null,
    val status: VoiceStatus = VoiceStatus.OFF,
) {
    val visible: Boolean get() = listening || heard != null || answer != null
}

/**
 * Qué está haciendo el asistente con lo que oye. Existe porque sin botón de
 * hablar no había forma de distinguir "no me oyó" de "me está atendiendo".
 */
enum class VoiceStatus {
    /** Sin micrófono: el aviso de permisos ya lo explica. */
    OFF,
    /** El dispositivo no puede reconocer la voz: un micrófono tachado, de recordatorio. */
    UNAVAILABLE,
    /** Micrófono abierto, esperando la palabra de activación. */
    WAITING,
    /** Acaba de preguntar algo: es el turno del conductor. */
    YOUR_TURN,
    /** Alguien está hablando y aún no se sabe qué dijo. */
    HEARING,
    /** Se oyó la palabra y la orden: entendiendo o preparando la respuesta. */
    THINKING,
}

/** La tira del asistente, al pie: lo que se oyó y lo que se contesta. */
@Composable
private fun VoiceCard(voice: VoiceUi) {
    val d = CarTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.cardCorner))
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f))
            .padding(d.spaceS),
    ) {
        Text(
            // Sin esto, un "no te he oido" aparecia bajo el rotulo "You said",
            // atribuyendole al conductor una frase que no dijo.
            text = when {
                voice.listening -> stringResource(R.string.listening)
                voice.heard != null -> stringResource(R.string.you_said)
                else -> stringResource(R.string.brand)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        if (voice.heard != null) {
            Text(
                text = "“${voice.heard}”",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (voice.answer != null) {
            Spacer(Modifier.height(d.spaceXs))
            Text(
                text = voice.answer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val COMPACT_HEIGHT = 500.dp

/**
 * Una fila de acciones que pone **solo las que caben enteras** y reparte entre
 * ellas todo el ancho, a partes iguales. La estimación de
 * [CarMetrics.fitsActions] no sabe lo que mide cada etiqueta —"Dettagli" no es
 * "Details"—, y un botón recortado es peor que no ponerlo; repartir el ancho
 * evita además la fila pegada a la izquierda con un hueco a la derecha. Se
 * decide con el ancho intrínseco de cada botón, sin medirlo dos veces. El
 * primero se coloca siempre.
 */
@Composable
private fun ActionsThatFit(spacing: Dp, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val gap = spacing.roundToPx()
        var count = 0
        var used = 0
        for (measurable in measurables) {
            val width = measurable.maxIntrinsicWidth(constraints.maxHeight)
            val next = if (count == 0) width else used + gap + width
            if (count > 0 && next > constraints.maxWidth) break
            count++
            used = next
        }
        val each = ((constraints.maxWidth - gap * (count - 1)) / count.coerceAtLeast(1)).coerceAtLeast(0)
        val placed = measurables.take(count).map { it.measure(Constraints.fixedWidth(each)) }
        val height = placed.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            var x = 0
            placed.forEach {
                it.placeRelative(x, (height - it.height) / 2)
                x += each + gap
            }
        }
    }
}

@Composable
internal fun PlaceEntry(
    place: Place,
    distance: String,
    showDetails: Boolean,
    showNavigate: Boolean,
    dense: Boolean,
    hasStory: Boolean,
    onSelect: () -> Unit,
    onListen: () -> Unit,
    onNavigate: () -> Unit,
) {
    val d = CarTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.cardCorner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // La tarjeta entera abre el detalle, siempre: la fila de acciones
            // puede dejar fuera el botón si en esta pantalla no cabe.
            .clickable(onClick = onSelect)
            .padding(d.spaceS),
    ) {
        // Nombre y distancia comparten fila: en dos filas separadas costaban una
        // línea de más por tarjeta, que multiplicada por la lista es un lugar
        // menos a la vista.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (dense) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(d.spaceS))
            Text(
                text = distance,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(d.spaceXs))
        // Qué es y dónde, para lo que salió de una búsqueda: es lo que decide si
        // merece la pena ir.
        val about = listOfNotNull(place.kind, place.address).joinToString(" · ")
        if (!hasStory && about.isNotBlank()) {
            Text(
                text = about,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(d.spaceXs))
        ActionsThatFit(spacing = d.spaceXs) {
            if (hasStory) {
                CarButton(label = stringResource(R.string.listen), onClick = onListen, dense = dense)
                // Si no cabe, no se recorta: se deja para la ficha, que se abre
                // tocando la tarjeta y donde el botón sí tiene sitio.
                if (showNavigate) {
                    CarSecondaryButton(label = stringResource(R.string.take_me), onClick = onNavigate, dense = dense)
                }
            } else {
                // Sin historia, lo que se hace con un sitio es ir.
                CarButton(label = stringResource(R.string.take_me), onClick = onNavigate, dense = dense)
            }
            // El detalle solo tiene sentido con el coche parado: es texto largo.
            if (showDetails) {
                CarSecondaryButton(label = stringResource(R.string.details), onClick = onSelect, dense = dense)
            }
        }
    }
}

internal fun formatDistance(meters: Double): String = when {
    meters < 1_000 -> "%.0f m".format(meters)
    else -> "%.1f km".format(meters / 1_000)
}

private fun formatTime(ms: Int): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}


/** Lo que se buscó y lo que salió. */
data class SearchUi(val title: String, val places: List<Place>)

/** Una búsqueda de un toque: "Ristoranti", "Benzina". */
data class SearchShortcut(
    val label: String,
    /** La categoría que busca ("restaurant", "gas_station"…), para su icono. */
    val category: String? = null,
    val onClick: () -> Unit,
)

/** Un permiso que falta: qué se pierde sin él y cómo activarlo. */
data class PermissionNotice(
    val message: String,
    /** Sin acción, solo el aviso: lo que no se arregla tocando. */
    val action: String? = null,
    val onClick: () -> Unit = {},
)

@Composable
internal fun PermissionCard(notice: PermissionNotice) {
    val d = CarTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.cardCorner))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
            .padding(horizontal = d.spaceS, vertical = d.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = notice.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        notice.action?.let { action ->
            Spacer(Modifier.width(d.spaceS))
            CarSecondaryButton(label = action, onClick = notice.onClick, dense = true)
        }
    }
}

/**
 * El estado del asistente, en una línea: un punto y una palabra. Discreto en
 * reposo, visible cuando pasa algo. El punto late mientras oye; mientras
 * piensa, gira. Lo que no se ve aquí —la app hablando— ya lo dice la tarjeta de
 * lo que suena.
 */
@Composable
internal fun VoiceStatusChip(status: VoiceStatus, labelled: Boolean = true) {
    if (status == VoiceStatus.OFF) return
    val d = CarTheme.dimens
    if (status == VoiceStatus.UNAVAILABLE) {
        // Quieto, apagado y pequeño: solo recuerda que no hay voz, sin quitarle
        // sitio al nombre de la ciudad.
        Box(Modifier.padding(start = d.spaceXs)) {
            GlyphIcon(Glyph.MIC_OFF, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), MIC_OFF_ICON)
        }
        return
    }
    val active = status != VoiceStatus.WAITING
    val color = when (status) {
        VoiceStatus.THINKING, VoiceStatus.YOUR_TURN -> MaterialTheme.colorScheme.primary
        VoiceStatus.HEARING -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    val label = stringResource(
        when (status) {
            VoiceStatus.WAITING -> R.string.voice_waiting
            VoiceStatus.YOUR_TURN -> R.string.listening
            VoiceStatus.HEARING -> R.string.voice_hearing
            else -> R.string.voice_thinking
        },
    )
    val pulse = rememberInfiniteTransition(label = "voz")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (status == VoiceStatus.HEARING || status == VoiceStatus.YOUR_TURN) 0.25f else 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
        label = "latido",
    )
    Row(
        Modifier
            .clip(RoundedCornerShape(d.cardCorner))
            .background(if (active && labelled) color.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = d.spaceS, vertical = d.spaceXs / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dot = d.spaceS * 0.8f
        if (status == VoiceStatus.THINKING) {
            CircularProgressIndicator(modifier = Modifier.size(dot), color = color, strokeWidth = dot / 5)
        } else {
            Box(Modifier.size(dot).alpha(alpha).clip(CircleShape).background(color))
        }
        if (labelled) {
            Spacer(Modifier.width(d.spaceXs))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = if (active) color else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

private const val PULSE_MS = 600

/** El micrófono tachado: del tamaño del punto de escucha al que sustituye, poco más. */
private val MIC_OFF_ICON: Dp @Composable @ReadOnlyComposable get() = CarTheme.metrics.grown(14.dp)
