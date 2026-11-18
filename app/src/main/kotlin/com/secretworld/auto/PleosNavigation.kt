package com.secretworld.auto

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import com.secretworld.auto.domain.Place
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * El navegador de Pleos, a través de su servicio de navegación (el que usará
 * NaviHelper cuando Pleos lo publique). Su app no responde a `geo:` ni a
 * enlaces: el destino se le pide al servicio, que se lo pasa al navegador.
 *
 * Sin la biblioteca, se habla con el servicio directamente: la interfaz
 * `INaviService` recibe pedidos de texto —quién pide, qué y los datos en JSON—.
 * Los nombres de los pedidos y de los campos son los del navegador del
 * emulador v2.1.5 (docs/NAVIGATION-AND-MAP-DECISIONS.md).
 */
class PleosNavigation(private val context: Context) {

    private var binder: IBinder? = null
    private var connecting: CompletableDeferred<IBinder?>? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            binder = service
            connecting?.complete(service)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            binder = null
        }
    }

    private val serviceIntent = Intent(ACTION).setPackage(SERVICE_PACKAGE)

    /** Si este coche tiene el navegador de Pleos. */
    val available: Boolean by lazy {
        context.packageManager.resolveService(serviceIntent, 0) != null &&
            context.packageManager.getLaunchIntentForPackage(NAVIGATOR_PACKAGE) != null
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
        val cold = !warmedUp
        if (!show()) return false
        val sent = routeTo(place)
        if (sent && cold) {
            delay(WARM_UP_MS)
            routeTo(place)
        }
        warmedUp = sent
        return sent
    }

    private var warmedUp = false

    /** Pide la ruta hasta [place]. */
    private suspend fun routeTo(place: Place): Boolean {
        val data = JSONObject()
            .put("latitude", place.position.latitude)
            .put("longitude", place.position.longitude)
            .put("poiName", place.name)
            // Todos los textos, aunque vayan vacíos: el navegador se cierra si
            // falta alguno (la dirección, la primera vez que se probó).
            .put("address", place.address ?: listOfNotNull(place.name, place.city).joinToString(", "))
            .put("poiId", "")
            .put("poiSubId", "")
            .put("routeOption", "RECOMMENDED")
        return request(REQUEST_ROUTE, data.toString())
    }

    /** Cancela la ruta que esté guiando. */
    suspend fun cancel(): Boolean = request(REQUEST_ROUTE_CANCEL, "{}")

    /** El navegador delante: Pleos lo pone en el hueco grande y a nosotros en el pequeño. */
    private fun show(): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(NAVIGATOR_PACKAGE) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "no se pudo abrir el navegador", it) }
            .isSuccess
    }

    fun release() {
        if (binder != null || connecting != null) runCatching { context.unbindService(connection) }
        binder = null
        connecting = null
    }

    private suspend fun request(type: String, data: String): Boolean {
        val service = connect() ?: return false
        return withContext(Dispatchers.IO) {
            val input = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                input.writeInterfaceToken(DESCRIPTOR)
                input.writeString(context.packageName)
                input.writeString(type)
                input.writeString(data)
                service.transact(TRANSACTION_REQUEST_TO_NAVI, input, reply, 0)
                reply.readException()
                Log.i(TAG, "$type → $data")
                true
            } catch (e: Exception) {
                Log.w(TAG, "el navegador no aceptó $type", e)
                false
            } finally {
                input.recycle()
                reply.recycle()
            }
        }
    }

    private suspend fun connect(): IBinder? {
        binder?.takeIf { it.isBinderAlive }?.let { return it }
        val waiting = CompletableDeferred<IBinder?>().also { connecting = it }
        val bound = runCatching { context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE) }
            .onFailure { Log.w(TAG, "no se pudo conectar con el servicio de navegación", it) }
            .getOrDefault(false)
        if (!bound) return null
        return withTimeoutOrNull(CONNECT_MS) { waiting.await() }
    }

    private companion object {
        const val TAG = "SWPleosNavi"
        const val SERVICE_PACKAGE = "ai.pleos.playground.service.navi"
        const val NAVIGATOR_PACKAGE = "ai.umos.maps.android.navigation.app"
        const val ACTION = "ai.pleos.playground.intent.action.NAVI_START"
        const val DESCRIPTOR = "ai.pleos.playground.navi.INaviService"

        /** `requestToNavi(applicationId, type, data)`, la tercera de la interfaz. */
        const val TRANSACTION_REQUEST_TO_NAVI = IBinder.FIRST_CALL_TRANSACTION + 2
        const val REQUEST_ROUTE = "RequestRoute"
        const val REQUEST_ROUTE_CANCEL = "RequestRouteCancel"
        const val CONNECT_MS = 3_000L
        const val WARM_UP_MS = 10_000L
    }
}
