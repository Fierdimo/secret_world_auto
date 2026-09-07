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
    offline: Boolean,
    onSelect: (Place) -> Unit,
    onListen: (Place) -> Unit,
    onNavigate: (Place) -> Unit,
    modifier: Modifier = Modifier,
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
        PanelHeader(city = city, offline = offline)

        if (CarTheme.metrics.heightDp >= COMPACT_HEIGHT) {
            Spacer(Modifier.height(d.spaceM))
            Text(
                text = if (driving) "On your route" else "Around you",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(Modifier.height(d.spaceS))

        if (places.isEmpty()) {
            // Preferir el silencio al relleno vale también para la pantalla.
            Text(
                text = "Nothing worth telling around here yet.",
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
        if (nowPlaying != null) {
            Spacer(Modifier.height(d.spaceS))
            NowPlayingCard(nowPlaying)
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
            text = if (state.preparing) "Preparing" else "Now playing",
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
private fun PanelHeader(city: String?, offline: Boolean) {
    val place = city ?: "Finding where you are"
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
                    text = "  ·  offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        return
    }

    Column {
        Text("Secret World", style = MaterialTheme.typography.displayMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = place,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (offline) {
                Text(
                    text = "  ·  offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
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
            CarButton(label = "Listen", onClick = onListen, dense = dense)
            // Si no cabe, no se recorta: se deja para la ficha, que se abre
            // tocando la tarjeta y donde el botón sí tiene sitio.
            if (showNavigate) {
                CarSecondaryButton(label = "Take me", onClick = onNavigate, dense = dense)
            }
            // El detalle solo tiene sentido con el coche parado: es texto largo.
            if (showDetails) {
                CarSecondaryButton(label = "Details", onClick = onSelect, dense = dense)
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

