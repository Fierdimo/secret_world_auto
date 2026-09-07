package com.secretworld.auto.core.data

import com.secretworld.auto.domain.VoiceLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaceMapperTest {

    private fun dto(
        oid: Long = 1,
        name: String = "Colosseum - Secret World ",
        description: String? = "The **Colosseum** stands as a monumental testament to Roman " +
            "engineering. Built under Vespasian in 72 AD, it seated about seventy thousand " +
            "people and hosted spectacles that showed the reach of the empire.",
        historical: String? = null,
        lat: Double? = 41.8902,
        lon: Double? = 12.4922,
        city: String? = "Roma",
    ) = PlaceDto(
        oid = oid,
        name = name,
        description = description,
        historicalDescription = historical,
        imagePath = "https://sworld.co.uk/119/photoAlbum/7614/0.jpeg",
        languageCode = "en",
        address = PlaceAddressDto(lat, lon, city?.let { NamedDto(it) }, NamedDto("Italia")),
    )

    @Test
    fun `mapea un lugar completo`() {
        val place = PlaceMapper.toDomain(dto(), VoiceLanguage.ENGLISH)

        assertNotNull(place)
        assertEquals("1", place.id)
        assertEquals("Roma", place.city)
        assertEquals(41.8902, place.position.latitude)
        assertTrue(place.hasNarrationIn(VoiceLanguage.ENGLISH))
    }

    @Test
    fun `quita el sufijo de marca del nombre`() {
        val place = PlaceMapper.toDomain(dto(), VoiceLanguage.ENGLISH)
        assertEquals("Colosseum", place?.name)
    }

    @Test
    fun `limpia el marcado del texto narrado`() {
        val place = PlaceMapper.toDomain(dto(), VoiceLanguage.ENGLISH)
        assertNotNull(place?.summary)
        assertTrue("**" !in place.summary!!, "el Markdown no se narra")
        assertTrue(place.summary!!.startsWith("The Colosseum stands"))
    }

    @Test
    fun `descarta un lugar sin coordenadas`() {
        assertNull(PlaceMapper.toDomain(dto(lat = null), VoiceLanguage.ENGLISH))
        assertNull(PlaceMapper.toDomain(dto(lon = null), VoiceLanguage.ENGLISH))
    }

    @Test
    fun `descarta un lugar sin texto`() {
        assertNull(PlaceMapper.toDomain(dto(description = null), VoiceLanguage.ENGLISH))
    }

    @Test
    fun `descarta articulos de marketing, que llegan sin ciudad`() {
        val articulo = dto(
            name = "Secret World vs Wanderlog: Best Trip Planner for Rome 2026",
            city = null,
        )
        assertNull(
            PlaceMapper.toDomain(articulo, VoiceLanguage.ENGLISH),
            "sin ciudad no es un lugar que se pueda narrar a un conductor",
        )
    }

    @Test
    fun `usa la descripcion historica cuando no hay descripcion`() {
        val place = PlaceMapper.toDomain(
            dto(description = null, historical = "A gate in the Aurelian Walls, built in the " +
                "third century and rebuilt many times over the following centuries by popes."),
            VoiceLanguage.ENGLISH,
        )
        assertNotNull(place)
        assertTrue(place.summary!!.startsWith("A gate in the Aurelian Walls"))
    }

    @Test
    fun `compone la url de la foto desde place_media, no desde image_path`() {
        val conMedia = dto().copy(
            media = listOf(
                PlaceMediaDto(MediaDto(filePath = "7/302719/originals/", fileName = "0", fileExtension = "jpg")),
            ),
        )

        assertEquals(
            "https://sworld.co.uk/img/img/7/302719///previews/0.jpg",
            PlaceMapper.imageUrlOf(conMedia),
            "cuando el fichero se llama 0, la imagen vive bajo previews/",
        )
    }

    @Test
    fun `sin place_media no hay foto`() {
        assertNull(PlaceMapper.imageUrlOf(dto()))
    }

    @Test
    fun `estima la duracion a partir de la longitud del texto`() {
        val place = PlaceMapper.toDomain(dto(), VoiceLanguage.ENGLISH)
        assertNotNull(place)
        assertTrue(place.narrationSeconds in 20..180)
    }
}
