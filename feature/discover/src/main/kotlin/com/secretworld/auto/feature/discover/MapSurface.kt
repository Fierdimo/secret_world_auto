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
 * el mapa propio es de OpenStreetMap y que en el vehículo se usará el de
 * su navegador. Para que eso no sea una reescritura, todo el resto de
 * la app habla con el mapa a través de este estado y nada más.
 */
data class MapState(
    val vehicle: VehicleState,
    val route: List<Coordinates>,
    val places: List<Place>,
    val highlighted: String?,
    /**
     * Qué fracción del borde izquierdo del mapa queda **tapada por el panel**.
     *
     * El mapa ocupa el fondo entero, así que su centro geométrico cae debajo del
     * panel. Sin corregirlo, el vehículo y los lugares quedarían escondidos justo
     * detrás de la información. Cada implementación desplaza su foco con esto.
     */
    val panelCoverFraction: Float = 0f,
    /**
     * Puntos a enseñar juntos —lo que se acaba de buscar—. Con ellos, la cámara
     * los encuadra en vez de seguir al vehículo: si se pidió un restaurante en
     * otro barrio, lo que interesa ver es ese barrio.
     */
    val frame: List<Coordinates> = emptyList(),
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

/**
 * La presentación de un itinerario recién propuesto: la cámara vuela la ruta
 * entera, parada a parada, sobre un mapa simplificado. La dibuja cada sabor
 * (`TourFlyover`); aquí solo lo que necesita saber.
 *
 * @param path el trazado por calles de principio a fin, o las paradas unidas en
 *   línea recta si no se pudo calcular.
 * @param legEnds dónde acaba cada tramo dentro de [path]: el trazado se revela
 *   tramo a tramo, al ritmo de la cámara.
 */
data class FlyoverState(
    val start: Coordinates,
    val stops: List<Place>,
    val times: List<String>,
    val path: List<Coordinates>,
    val legEnds: List<Int>,
    /** "4 paradas · 2 horas y 35". */
    val summary: String,
    /** Si toca la presentación; después la vista se queda, sin repetirla. */
    val intro: Boolean = true,
    /**
     * El sitio que se está contando: la cámara se acerca a él y, al quitarlo,
     * vuelve a la vista del itinerario. Puede no ser una de las paradas.
     */
    val focus: Place? = null,
    /**
     * Lo que hay alrededor y no está en el itinerario: en pequeño en el mapa,
     * para añadirlo tocándolo. Armándolo a mano, es todo lo que hay.
     */
    val candidates: List<Place> = emptyList(),
    /** Tocar un sitio de alrededor es elegirlo (el inicio), no añadirlo: sin "+". */
    val pickable: Boolean = false,
    /** Dónde está el coche, si se enseña. */
    val me: Coordinates? = null,
    /** El radio de acción alrededor de [me], dibujado como un anillo y encuadrado entero. */
    val radiusMeters: Double? = null,
    /** Dónde está el centro del anillo si no es el coche: una zona buscada. */
    val ringCenter: Coordinates? = null,
    /** Si se está buscando: ondas desde el centro hasta el anillo, y los puntos esperan. */
    val scanning: Boolean = false,
    /**
     * Dónde se está, para el título de la presentación. La ciudad que trae
     * Wikipedia en cada parada puede ser un barrio lejano: «Bocagrande» estando
     * en el Centro.
     */
    val city: String? = null,
)
