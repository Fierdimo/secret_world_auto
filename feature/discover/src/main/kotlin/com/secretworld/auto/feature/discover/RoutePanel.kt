package com.secretworld.auto.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.secretworld.auto.feature.discover.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secretworld.auto.core.designsystem.CarButton
import com.secretworld.auto.core.designsystem.CarSecondaryButton
import com.secretworld.auto.core.designsystem.CarTheme
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
    onSpeak: () -> Unit,
    onSelect: (Place) -> Unit,
    onListen: (Place) -> Unit,
    onNavigate: (Place) -> Unit,
    modifier: Modifier = Modifier,
    navigation: NavigationUi? = null,
    onStopNavigation: () -> Unit = {},
) {
    val d = CarTheme.dimens
    val driving = mode == DrivingMode.DRIVING

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.93f))
            .safeDrawingPadding()
            // Arriba, lo justo para despegar el título de la barra de estado: cada
            // dp de margen decorativo aquí es un dp que pierde la lista, que es lo
            // único que el conductor va a leer.
            .padding(horizontal = d.spaceM, vertical = d.spaceXs),
    ) {
        PanelHeader(city = city, offline = offline, voice = voice, onSpeak = onSpeak)

        // La ruta activa va lo primero: mientras se navega es lo único que se
        // mira de reojo, y la lista de lugares pasa a segundo plano.
        if (navigation != null) {
            Spacer(Modifier.height(d.spaceS))
            NavigationCard(navigation, onStopNavigation)
        }

        if (CarTheme.metrics.heightDp >= COMPACT_HEIGHT) {
            Spacer(Modifier.height(d.spaceM))
            Text(
                text = stringResource(if (driving) R.string.section_driving else R.string.section_parked),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(Modifier.height(d.spaceS))

        if (places.isEmpty()) {
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
        LaunchedEffect(mode) { listState.scrollToItem(0) }

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
                places.size
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
                    items(places.take(limit), key = { it.id }) { place ->
                        PlaceEntry(
                            place = place,
                            distance = formatDistance(origin.distanceTo(place.position)),
                            // Se pregunta si cabe, no se supone por el tamaño de
                            // pantalla. Donde no cabe, el detalle se abre tocando la
                            // tarjeta entera, que es un objetivo mucho mayor.
                            showDetails = !driving &&
                                CarTheme.metrics.fitsActions(3, expanded = false),
                            showNavigate = CarTheme.metrics.fitsActions(2, expanded = false),
                            dense = !driving,
                            onSelect = { onSelect(place) },
                            onListen = { onListen(place) },
                            onNavigate = { onNavigate(place) },
                        )
                    }
                }

                if (places.size > limit) {
                    Spacer(Modifier.height(d.spaceXs))
                    Text(
                        text = if (driving) {
                            "${places.size - limit} more when you stop"
                        } else {
                            "${places.size - limit} more nearby"
                        },
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
            NowPlayingCard(nowPlaying)
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
    /** "Turn left onto Via Sacra", o null si el proveedor no da maniobras. */
    val instruction: String?,
    /** "in 200 m". */
    val instructionDistance: String?,
    val arrived: Boolean = false,
)

@Composable
private fun NavigationCard(state: NavigationUi, onStop: () -> Unit) {
    val d = CarTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.cardCorner))
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f))
            .padding(d.spaceS),
    ) {
        Text(
            text = if (state.arrived) stringResource(R.string.nav_arrived) else stringResource(R.string.nav_to, state.destination),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // La maniobra es lo grande: es lo que hay que hacer ahora.
        Text(
            text = state.instruction ?: state.destination,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = listOfNotNull(state.instructionDistance, "${state.remaining} · ${state.eta}").joinToString("  ·  "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            CarSecondaryButton(label = stringResource(R.string.nav_end), onClick = onStop, compact = true)
        }
    }
}

/** Lo que suena ahora, con progreso real. Sin esto la app parece muda aunque hable. */
data class NowPlaying(
    val placeName: String,
    val positionMs: Int,
    val durationMs: Int,
    val preparing: Boolean = false,
)

@Composable
private fun NowPlayingCard(state: NowPlaying) {
    val d = CarTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.cardCorner))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .padding(d.spaceS),
    ) {
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
    voice: VoiceUi,
    onSpeak: () -> Unit,
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
                modifier = Modifier.weight(1f, fill = false),
            )
            if (offline) {
                Text(
                    text = "  ·  " + stringResource(R.string.offline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(Modifier.width(CarTheme.dimens.spaceS))
            SpeakButton(voice, onSpeak)
        }
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(stringResource(R.string.brand), style = MaterialTheme.typography.displayMedium)
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
      SpeakButton(voice, onSpeak)
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
) {
    val visible: Boolean get() = listening || heard != null || answer != null
}

@Composable
private fun SpeakButton(voice: VoiceUi, onSpeak: () -> Unit) {
    // Escuchando, el botón deja de invitar y pasa a informar: es la unica senal
    // de que el microfono esta abierto, y eso no se puede dejar sin decir.
    // Con escucha permanente el botón deja de ser la forma de hablar y pasa a ser
    // dos cosas: **la señal de que el micrófono está vivo** —que no se puede
    // ocultar— y la salida para quien prefiera pulsar antes que decir el nombre.
    CarSecondaryButton(
        label = when {
            voice.listening -> stringResource(R.string.listening)
            voice.handsFree -> "“${voice.wakeWord}”"
            else -> stringResource(R.string.speak)
        },
        onClick = onSpeak,
        dense = true,
    )
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

@Composable
private fun PlaceEntry(
    place: Place,
    distance: String,
    showDetails: Boolean,
    showNavigate: Boolean,
    dense: Boolean,
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
            // Donde no caben todas las acciones, el objetivo es la tarjeta entera.
            .clickable(enabled = !showDetails, onClick = onSelect)
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
        Row(horizontalArrangement = Arrangement.spacedBy(d.spaceXs)) {
            CarButton(label = stringResource(R.string.listen), onClick = onListen, dense = dense)
            // Si no cabe, no se recorta: se deja para la ficha, que se abre
            // tocando la tarjeta y donde el botón sí tiene sitio.
            if (showNavigate) {
                CarSecondaryButton(label = stringResource(R.string.take_me), onClick = onNavigate, dense = dense)
            }
            // El detalle solo tiene sentido con el coche parado: es texto largo.
            if (showDetails) {
                CarSecondaryButton(label = stringResource(R.string.details), onClick = onSelect, dense = dense)
            }
        }
    }
}

private fun formatDistance(meters: Double): String = when {
    meters < 1_000 -> "%.0f m".format(meters)
    else -> "%.1f km".format(meters / 1_000)
}

private fun formatTime(ms: Int): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

