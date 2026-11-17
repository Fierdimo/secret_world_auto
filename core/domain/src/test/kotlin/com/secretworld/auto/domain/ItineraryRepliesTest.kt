package com.secretworld.auto.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItineraryRepliesTest {

    @Test
    fun `cada respuesta de tiempo se entiende como tiempo`() {
        VoiceLanguage.entries.forEach { language ->
            ItineraryReplies.time(language).forEach { reply ->
                assertTrue(ItineraryRequests.details(reply.says).hasTime, "$language: «${reply.says}»")
            }
        }
    }

    @Test
    fun `cada respuesta de gustos se entiende como su gusto`() {
        val expected = listOf(Interest.HISTORY, Interest.ART, Interest.RELIGION, Interest.NATURE, Interest.CITY)
        VoiceLanguage.entries.forEach { language ->
            ItineraryReplies.interests(language).zip(expected).forEach { (reply, interest) ->
                assertEquals(setOf(interest), ItineraryRequests.details(reply.says).interests, "$language: «${reply.says}»")
            }
        }
    }

    @Test
    fun `primero el tiempo y luego los gustos`() {
        val both = setOf(ItineraryRequest.Missing.TIME, ItineraryRequest.Missing.INTERESTS)
        assertEquals(ItineraryReplies.time(VoiceLanguage.SPANISH), ItineraryReplies.forMissing(both, VoiceLanguage.SPANISH))
        assertEquals(
            ItineraryReplies.interests(VoiceLanguage.SPANISH),
            ItineraryReplies.forMissing(setOf(ItineraryRequest.Missing.INTERESTS), VoiceLanguage.SPANISH),
        )
    }
}
