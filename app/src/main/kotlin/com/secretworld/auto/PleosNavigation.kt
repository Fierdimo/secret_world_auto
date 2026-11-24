package com.secretworld.auto

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.content.Intent
import android.util.Log
import com.secretworld.auto.domain.Place
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * El navegador de Pleos, con su SDK oficial (`NaviHelper` 3.0.5). El destino se
 * le pide al servicio de navegación, que se lo pasa al navegador que tenga el
 * coche: no depende de un paquete concreto. En Waterfall el navegador es
 * `com.autoever.navi`, no el `ai.umos.maps…` de la v2.1.5.
 */
class PleosNavigation(private val context: Context) {

    private var helper: NaviHelper? = null
    private var initialized: CompletableDeferred<Boolean>? = null
    private var warmedUp = false

    private val events = object : NaviHelperEventListener {
        override fun onRouteStarted(info: ai.pleos.playground.navi.data.RouteStartInfo) {
            Log.i(TAG, "ruta iniciada")
        }

        override fun onRouteCancelled() {
            Log.i(TAG, "ruta cancelada")
        }

        override fun onError(code: NaviErrorCode) {
            Log.w(TAG, "el navegador no pudo: $code")
        }
    }

    /**
     * Si este coche tiene el servicio de navegación de Pleos y un navegador que
     * lo atienda. En Waterfall el navegador viene oculto y su motor no carga:
     * entregarle la ruta sería dejar al conductor sin ninguna, y la app usa la suya.
     */
    val available: Boolean by lazy {
        context.packageManager.resolveService(Intent(ACTION).setPackage(SERVICE_PACKAGE), 0) != null &&
            NAVIGATORS.any { context.packageManager.getLaunchIntentForPackage(it) != null }
    }

    /**
     * Pone el navegador delante y le pide la ruta hasta [place]. `true` si el
     * servicio la aceptó.
     *
     * El servicio no guarda los pedidos: si el navegador aún no se ha conectado
     * a él, la ruta se pierde. Recién abierto tarda unos segundos, así que la
     * primera vez se repite cuando ya escucha.
     */
    suspend fun open(place: Place): Boolean {
        val navi = ready() ?: return false
        val cold = !warmedUp
        show()
        val sent = routeTo(navi, place)
        if (sent && cold) {
            delay(WARM_UP_MS)
            routeTo(navi, place)
        }
        warmedUp = sent
        return sent
    }

    /** Cancela la ruta que esté guiando. */
    suspend fun cancel(): Boolean {
        val navi = ready() ?: return false
        return call("cancelRoute") { navi.cancelRoute() }
    }

    fun release() {
        helper?.let { navi ->
            runCatching {
                navi.removeListener(events)
                navi.release()
            }
        }
        helper = null
        initialized = null
    }

    private suspend fun routeTo(navi: NaviHelper, place: Place): Boolean {
        val route = RouteInfo(
            latitude = place.position.latitude,
            longitude = place.position.longitude,
            poiName = place.name,
            // Todos los textos, aunque vayan vacíos: el navegador de la v2.1.5 se
            // cerraba si faltaba alguno.
            poiId = "",
            address = place.address ?: listOfNotNull(place.name, place.city).joinToString(", "),
            poiSubId = "",
            routeOption = RouteOption.RECOMMENDED,
        )
        return call("requestRoute ${place.name}") { navi.requestRoute(route) }
    }

    /**
     * El navegador delante: Pleos lo pone en el hueco grande y a nosotros en el
     * pequeño. Se abre él mismo, no con `requestNavigationScreen`: ese pide
     * `NAVI_CUSTOM_MAP`, que la app no tiene aprobado.
     */
    private fun show() {
        val launch = NAVIGATORS.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) } ?: return
        runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Log.w(TAG, "no se pudo abrir el navegador", it) }
    }

    /** Crea el cliente y espera a que se conecte al servicio. En el hilo principal. */
    private suspend fun ready(): NaviHelper? = withContext(Dispatchers.Main) {
        if (!available) return@withContext null
        val waiting = initialized ?: CompletableDeferred<Boolean>().also { done ->
            initialized = done
            val navi = runCatching { NaviHelper(context) }
                .onFailure { Log.e(TAG, "NaviHelper: no se pudo crear", it) }
                .getOrNull()
            helper = navi
            if (navi == null) {
                done.complete(false)
                return@also
            }
            navi.addListener(events)
            runCatching {
                navi.initialize(
                    { done.complete(true) },
                    { error ->
                        Log.w(TAG, "NaviHelper no se conecta", error)
                        done.complete(false)
                    },
                )
            }.onFailure {
                Log.e(TAG, "NaviHelper initialize", it)
                done.complete(false)
            }
        }
        val ok = withTimeoutOrNull(CONNECT_MS) { waiting.await() } ?: false
        // Un fallo no se queda para siempre: el siguiente pedido lo vuelve a intentar.
        if (!ok) release()
        helper.takeIf { ok }
    }

    private suspend fun call(what: String, block: () -> Unit): Boolean = withContext(Dispatchers.Main) {
        runCatching(block)
            .onSuccess { Log.i(TAG, what) }
            .onFailure { Log.w(TAG, "el navegador no aceptó $what", it) }
            .isSuccess
    }

    private companion object {
        const val TAG = "SWPleosNavi"
        const val SERVICE_PACKAGE = "ai.pleos.playground.service.navi"
        const val ACTION = "ai.pleos.playground.intent.action.NAVI_START"

        /** Los navegadores conocidos: el de la v2.1.5 y el de Waterfall. */
        val NAVIGATORS = listOf("ai.umos.maps.android.navigation.app", "com.autoever.navi")
        const val CONNECT_MS = 5_000L
        const val WARM_UP_MS = 10_000L
    }
}
