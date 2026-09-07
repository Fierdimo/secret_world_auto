package com.secretworld.auto.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.secretworld.auto.feature.discover.CanvasMap
import com.secretworld.auto.feature.discover.MapState

/**
 * El mapa en el **vehículo**.
 *
 * Decisión de producto: en el coche se usará **el servicio de mapas del propio
 * dispositivo**, no un proveedor licenciado por nosotros. Eso evita el contrato de
 * automoción y encaja visualmente con el resto del sistema.
 *
 * Hoy no se puede: el mapa nativo de Pleos vive detrás de `NaviService` y su
 * librería cliente no está entregada. Hasta entonces, el lienzo propio — que no
 * necesita red, ni claves, ni licencia.
 *
 * **Este build no incluye Mapbox**: su SDK solo se enlaza en el sabor de banco de
 * pruebas, así que el APK del vehículo no lo lleva ni pesa por él.
 */
@Composable
fun PlatformMap(state: MapState, modifier: Modifier = Modifier) {
    CanvasMap(state, modifier)
}
