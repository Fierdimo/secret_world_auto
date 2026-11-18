package com.secretworld.auto.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaceKindNamesTest {

    @Test
    fun `los tipos conocidos salen en el idioma de la app`() {
        assertEquals("Restaurante", PlaceKindNames.label("food", VoiceLanguage.SPANISH))
        assertEquals("Ristorante", PlaceKindNames.label("restaurant", VoiceLanguage.ITALIAN))
        assertEquals("Gasolinera", PlaceKindNames.label("fuel", VoiceLanguage.SPANISH))
        assertEquals("Fast food", PlaceKindNames.label("fast_food", VoiceLanguage.ENGLISH))
        assertEquals("Comida rápida", PlaceKindNames.label("fast food", VoiceLanguage.SPANISH))
    }

    @Test
    fun `lo desconocido sale tal cual y lo vacio no sale`() {
        assertEquals("Zoo", PlaceKindNames.label("zoo", VoiceLanguage.SPANISH))
        assertNull(PlaceKindNames.label(" ", VoiceLanguage.SPANISH))
        assertNull(PlaceKindNames.label(null, VoiceLanguage.SPANISH))
    }
}
