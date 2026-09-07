package com.secretworld.auto.map

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotation
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.MapStyle
import com.secretworld.auto.core.designsystem.CarTheme
import com.secretworld.auto.feature.discover.CanvasMap
import com.secretworld.auto.feature.discover.MapState

/**
 * El mapa en el **banco de pruebas**.
 *
 * Mapbox se usa **solo aquí**, para que la demostración enseñe calles reales. En
 * el vehículo manda `app/src/vehicle`, que usa el mapa del dispositivo.
 *
 * La clave pública **no viaja en el APK**: la sirve el backend de Secret World en
 * tiempo de ejecución, igual que en la app móvil. Mientras no llega, se dibuja el
 * lienzo propio en vez de una pantalla vacía.
 */
@OptIn(MapboxExperimental::class)
@Composable
fun PlatformMap(state: MapState, modifier: Modifier = Modifier) {
    val token = state.mapboxToken
    if (token.isNullOrBlank()) {
        CanvasMap(state, modifier)
        return
    }
    MapboxOptions.accessToken = token

    // Los marcadores también escalan: en una pantalla de coche, un círculo de 8
    // unidades es una cabeza de alfiler.
    val scale = CarTheme.metrics.scale
    val centre = state.vehicle.position
    // El panel flota sobre el borde izquierdo del mapa. Este relleno mueve el
    // centro de cámara hacia la derecha, para que el vehículo y los lugares no
    // queden escondidos justo debajo de la información.
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val leftInset = with(density) {
        (configuration.screenWidthDp.dp * state.panelCoverFraction).toPx().toDouble()
    }
    // La barra inferior también tapa mapa: sin este margen el vehículo queda
    // justo debajo de ella y no se ve dónde está uno.
    val bottomInset = with(density) { BOTTOM_BAR.toPx().toDouble() }

    val viewport = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(centre.longitude, centre.latitude))
            zoom(CITY_ZOOM)
            bearing(state.vehicle.headingDegrees ?: 0.0)
            pitch(DRIVING_PITCH)
            padding(EdgeInsets(0.0, leftInset, bottomInset, 0.0))
        }
    }

    MapboxMap(
        modifier = modifier,
        mapViewportState = viewport,
        // Oscuro: se conduce de noche y con sol de frente, y el mapa no debe
        // competir con el panel por la atención.
        style = { MapStyle(style = DARK_STYLE) },
        // La escala y la brújula, fuera de la esquina donde empieza el panel:
        // reservarles la franja superior izquierda le roba alto a lo único que
        // el conductor necesita leer. Se colocan por los huecos que ofrece el
        // propio composable, no tocando los plugins del MapView — que en Compose
        // no están montados todavía y devuelven null.
        scaleBar = {
            ScaleBar(
                alignment = Alignment.BottomEnd,
                // Por encima de la barra de utilidad, que ocupa esa misma esquina.
                contentPadding = PaddingValues(
                    start = ORNAMENT_MARGIN,
                    top = ORNAMENT_MARGIN,
                    end = ORNAMENT_MARGIN,
                    bottom = CarTheme.dimens.utilityBarHeight + CarTheme.dimens.spaceL,
                ),
                // Por defecto ocupa media pantalla. Es una referencia, no un dato
                // que el conductor lea: con un cuarto del ancho sobra.
                ratio = SCALE_RATIO,
                height = SCALE_HEIGHT,
                borderWidth = SCALE_BORDER,
                textSize = CarTheme.text.caption * SCALE_TEXT_SHARE,
            )
        },
        // Sin brújula: el mapa ya gira con el rumbo y el norte no es lo que se
        // mira al conducir. Ocupaba la esquina donde ahora van los créditos.
        compass = {},
        // Los créditos de Mapbox son obligatorios y deben verse: arriba a la
        // derecha, que es la única esquina que no tapa nada.
        logo = { Logo(alignment = Alignment.TopEnd, contentPadding = PaddingValues(ORNAMENT_MARGIN)) },
        attribution = {
            Attribution(
                alignment = Alignment.TopEnd,
                contentPadding = PaddingValues(ORNAMENT_MARGIN),
            )
        },
    ) {
        MapEffect(centre, state.vehicle.headingDegrees) { _ ->
            viewport.setCameraOptions {
                center(Point.fromLngLat(centre.longitude, centre.latitude))
                bearing(state.vehicle.headingDegrees ?: 0.0)
                padding(EdgeInsets(0.0, leftInset, bottomInset, 0.0))
            }
        }

        // Los lugares del catálogo sobre el mapa. Sin esto el mapa sería
        // decorativo: bonito y sin relación con lo que la app está contando.
        state.places.forEach { place ->
            val isHighlighted = place.id == state.highlighted
            CircleAnnotation(
                point = Point.fromLngLat(place.position.longitude, place.position.latitude),
            ) {
                circleRadius = (if (isHighlighted) 11.0 else 8.0) * scale
                circleColor = if (isHighlighted) Highlight else Marker
                circleStrokeWidth = 3.0 * scale
                circleStrokeColor = Ink
            }
        }

        // El vehículo, en su propio color y por encima de todo lo demás.
        CircleAnnotation(
            point = Point.fromLngLat(centre.longitude, centre.latitude),
        ) {
            circleRadius = 10.0 * scale
            circleColor = Highlight
            circleStrokeWidth = 4.0 * scale
            circleStrokeColor = Ink
        }
    }
}

/** Los mismos colores que el sistema de diseño, para que el mapa no desentone. */
private val Highlight = Color(0xFFE8B04B)
private val Marker = Color(0xFF7FB0A5)
private val Ink = Color(0xFF101112)

/** El mismo estilo que usa la app móvil, para que la marca se reconozca. */
private const val DARK_STYLE = "mapbox://styles/mapbox/dark-v11"
private const val CITY_ZOOM = 14.5
/** Algo de inclinación: en un coche mirar el mapa en picado desorienta. */
private const val DRIVING_PITCH = 45.0
private val ORNAMENT_MARGIN = 12.dp
private const val SCALE_RATIO = 0.22f
private val SCALE_HEIGHT = 3.dp
private val SCALE_BORDER = 1.dp
private const val SCALE_TEXT_SHARE = 0.7f
private val BOTTOM_BAR = 96.dp
