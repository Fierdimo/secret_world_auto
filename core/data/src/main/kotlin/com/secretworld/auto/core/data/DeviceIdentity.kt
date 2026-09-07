package com.secretworld.auto.core.data

import android.content.Context
import java.util.UUID

/**
 * Cómo se identifica este coche ante el backend, **sin cuenta y sin login**.
 *
 * El `device-token` se genera una vez y se conserva: es lo que permite que
 * favoritos y progreso tengan continuidad antes de que el usuario vincule nada.
 */
class DeviceIdentity private constructor(
    val deviceToken: String,
    val buildNumber: String,
    val platform: String,
) {
    companion object {
        /**
         * Se envía `android` porque es lo que el backend acepta hoy.
         *
         * [docs/BACKEND-REUSE.md] pide un valor propio —`pleos`— para poder
         * separar el tráfico del coche del de móvil en analítica. Es un cambio de
         * backend pendiente, no un olvido: mandar un valor que el servidor no
         * conoce arriesga que rechace las peticiones.
         */
        const val PLATFORM_TODAY = "android"
        const val PLATFORM_WANTED = "pleos"

        private const val PREFS = "sw_device"
        private const val KEY_TOKEN = "device_token"

        fun of(context: Context, buildNumber: String = "1"): DeviceIdentity {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val token = prefs.getString(KEY_TOKEN, null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(KEY_TOKEN, it).apply()
            }
            return DeviceIdentity(token, buildNumber, PLATFORM_TODAY)
        }

        /** Para tests, sin Android de por medio. */
        fun fake(token: String = "test-token") = DeviceIdentity(token, "1", PLATFORM_TODAY)
    }
}
