package com.secretworld.auto.map

import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.secretworld.auto.core.designsystem.CarTheme
import com.secretworld.auto.domain.Coordinates
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.SymbolLayer
import kotlin.coroutines.resume

/**
 * Un mapa pequeño y quieto que señala un sitio: el de la ficha. Sin gestos —está
 * para situarlo, no para explorar— y con el marcador en el centro, que es donde
 * cae el sitio. De OpenStreetMap, como el grande.
 */
@Composable
fun SpotMap(at: Coordinates, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val night = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    LaunchedEffect(mapView, night, at) {
        val map = suspendCancellableCoroutine { done -> mapView.getMapAsync { done.resume(it) } }
        map.uiSettings.apply {
            setAllGesturesEnabled(false)
            isCompassEnabled = false
            isLogoEnabled = false
            isAttributionEnabled = true
            attributionGravity = Gravity.BOTTOM or Gravity.END
            with(density) { setAttributionMargins(0, 0, 6.dp.roundToPx(), 6.dp.roundToPx()) }
        }
        map.cameraPosition = CameraPosition.Builder().target(LatLng(at.latitude, at.longitude)).zoom(SPOT_ZOOM).build()
        val loaded = suspendCancellableCoroutine { done ->
            map.setStyle(Style.Builder().fromUri(if (night) NIGHT_STYLE else DAY_STYLE)) { done.resume(it) }
        }
        // Con los nombres de las calles: aquí sí ayudan a situarse.
        loaded.layers.filterIsInstance<SymbolLayer>().filter { it.id.contains("poi", ignoreCase = true) }.forEach { loaded.removeLayer(it) }
    }
    val accent = MaterialTheme.colorScheme.primary
    val d = CarTheme.dimens
    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        // El sitio: un punto con halo, en el centro.
        Box(Modifier.align(Alignment.Center).size(d.spaceM).clip(CircleShape).background(accent.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(d.spaceS).clip(CircleShape).border(2.dp, Color.White, CircleShape).background(accent))
        }
    }
}

private const val NIGHT_STYLE = "https://tiles.openfreemap.org/styles/dark"
private const val DAY_STYLE = "https://tiles.openfreemap.org/styles/positron"
private const val SPOT_ZOOM = 15.5

/** Si esta build tiene mapa para la ficha. */
const val hasSpotMap = true
