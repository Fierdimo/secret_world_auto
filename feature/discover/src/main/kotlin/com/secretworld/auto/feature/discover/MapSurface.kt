package com.secretworld.auto.feature.discover

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.Place
import com.secretworld.auto.domain.VehicleState

/**
 * Todo lo que un mapa necesita saber para dibujarse.
 *
 * Existe para que **el mapa sea sustituible**. La decisión de producto es que
 * Mapbox se usa solo en el MVP y que en el vehículo se usará el servicio de mapas
 * que traiga el dispositivo. Para que eso no sea una reescritura, todo el resto de
 * la app habla con el mapa a través de este estado y nada más.
 */
data class MapState(
    val vehicle: VehicleState,
    val route: List<Coordinates>,
    val places: List<Place>,
    val highlighted: String?,
    /** Clave de Mapbox, si la implementación activa la necesita y el backend la dio. */
    val mapboxToken: String? = null,
    /**
     * Qué fracción del borde izquierdo del mapa queda **tapada por el panel**.
     *
     * El mapa ocupa el fondo entero, así que su centro geométrico cae debajo del
     * panel. Sin corregirlo, el vehículo y los lugares quedarían escondidos justo
     * detrás de la información. Cada implementación desplaza su foco con esto.
     */
    val panelCoverFraction: Float = 0f,
)

/**
 * El mapa dibujado por nosotros. Es la implementación **siempre disponible**: no
 * necesita red, ni claves, ni licencia, así que sirve de respaldo de cualquier
 * otra y de mapa por defecto donde no haya nada mejor.
 */
@Composable
fun CanvasMap(state: MapState, modifier: Modifier = Modifier) {
    MapCanvas(
        vehicle = state.vehicle,
        route = state.route,
        places = state.places,
        highlighted = state.highlighted,
        modifier = modifier,
        panelCoverFraction = state.panelCoverFraction,
    )
}
