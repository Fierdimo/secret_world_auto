package com.secretworld.auto.core.data

import com.secretworld.auto.domain.Coordinates
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CatalogueRepositoryTest {

    private val rome = Coordinates(41.9028, 12.4964)

    private class FakeApi(
        private val response: PlacesResponse? = null,
        private val error: Throwable? = null,
    ) : SecretWorldApi {
        var calls = 0
        override suspend fun placesNearby(request: PlacesRequest): PlacesResponse {
            calls++
            error?.let { throw it }
            return response!!
        }

        override suspend fun key(type: String): KeyResponse = KeyResponse(key = null)
    }

    private fun okResponse() = PlacesResponse(
        data = listOf(
            PlaceDto(
                oid = 7614,
                name = "Colosseum",
                description = "The Colosseum stands as a monumental testament to Roman " +
                    "engineering, built under Vespasian and finished by his son Titus.",
                imagePath = "https://sworld.co.uk/x.jpeg",
                address = PlaceAddressDto(41.8902, 12.4922, NamedDto("Roma"), NamedDto("Italia")),
            ),
        ),
    )

    @Test
    fun `sirve lugares del API cuando responde`() = runTest {
        val result = CatalogueRepository(FakeApi(response = okResponse())).nearby(rome)

        assertEquals(CatalogueRepository.Source.LIVE, result.source)
        assertEquals("Colosseum", result.places.single().name)
        assertNull(result.failure)
    }

    @Test
    fun `cae al respaldo cuando el API falla, y dice por que`() = runTest {
        val result = CatalogueRepository(FakeApi(error = java.io.IOException("sin red")))
            .nearby(rome)

        assertEquals(CatalogueRepository.Source.FALLBACK, result.source)
        assertTrue(result.places.isNotEmpty(), "nunca una pantalla vacia")
        assertEquals("sin red", result.failure)
    }

    @Test
    fun `cae al respaldo si el API responde sin lugares utilizables`() = runTest {
        // Solo articulos de marketing: llegan sin ciudad y el mapeador los descarta.
        val soloArticulos = PlacesResponse(
            data = listOf(
                PlaceDto(
                    oid = 1,
                    name = "Best AI Trip Planner in Rome 2026",
                    description = "Long marketing copy about planning trips with our product.",
                    address = PlaceAddressDto(41.9, 12.5, NamedDto(""), NamedDto("Italia")),
                ),
            ),
        )

        val result = CatalogueRepository(FakeApi(response = soloArticulos)).nearby(rome)

        assertEquals(CatalogueRepository.Source.FALLBACK, result.source)
        assertNotNull(result.failure)
    }

    @Test
    fun `no repite la llamada mientras el coche no se mueva apenas`() = runTest {
        val api = FakeApi(response = okResponse())
        val repository = CatalogueRepository(api)

        repository.nearby(rome)
        repository.nearby(Coordinates(rome.latitude + 0.0001, rome.longitude))

        assertEquals(1, api.calls, "unos metros no justifican otra llamada de red")
    }

    @Test
    fun `el texto narrado es el mismo que muestra la ficha`() = runTest {
        val repository = CatalogueRepository(FakeApi(response = okResponse()))
        val place = repository.nearby(rome).places.single()

        assertEquals(place.summary, repository.textFor(place, com.secretworld.auto.domain.VoiceLanguage.ENGLISH))
    }

    private fun assertNull(value: Any?) = kotlin.test.assertNull(value)
}
