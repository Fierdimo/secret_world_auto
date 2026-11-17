package com.secretworld.auto.core.data

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/** El API de Secret World, la parte que necesita el coche. */
interface SecretWorldApi {

    @POST("places/cards")
    suspend fun placesNearby(@Body request: PlacesRequest): PlacesResponse

    companion object {
        const val BASE_URL = "https://api.sworld.co.uk/api/v1/"

        fun create(identity: DeviceIdentity): SecretWorldApi {
            val json = Json {
                // La respuesta trae decenas de campos que no usamos, y el backend
                // añade más con el tiempo. Sin esto, cada cambio suyo nos rompe.
                ignoreUnknownKeys = true
                coerceInputValues = true
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(SECONDS_CONNECT, TimeUnit.SECONDS)
                .readTimeout(SECONDS_READ, TimeUnit.SECONDS)
                .addInterceptor(headers(identity))
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(SecretWorldApi::class.java)
        }

        /**
         * El esquema v2 del backend identifica al cliente sin login: `device-token`
         * más `build_number` y `platform`. Es justo lo que necesita el coche, donde
         * pedir credenciales es inviable (SPEC RF-5.1).
         */
        private fun headers(identity: DeviceIdentity) = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("api-version", "v1")
                .header("device-token", identity.deviceToken)
                .header("build_number", identity.buildNumber)
                .header("platform", identity.platform)
                .header("Content-Type", "application/json")
                .build()
            chain.proceed(request)
        }

        /** Cortos a propósito: en carretera, esperar treinta segundos no es esperar. */
        private const val SECONDS_CONNECT = 8L
        private const val SECONDS_READ = 12L
    }
}
