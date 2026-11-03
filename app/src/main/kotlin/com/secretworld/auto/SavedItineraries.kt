package com.secretworld.auto

import android.content.Context
import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.ItineraryDesigner
import com.secretworld.auto.domain.ItineraryProposal
import com.secretworld.auto.domain.ItineraryRequest
import com.secretworld.auto.domain.Pace
import com.secretworld.auto.domain.Place
import com.secretworld.auto.domain.SavedItinerary
import com.secretworld.auto.domain.VoiceLanguage
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Los itinerarios confirmados, guardados en el dispositivo: confirmar no es
 * arrancar, y se pueden armar en el hotel y empezar otro día, el que toque. El
 * más reciente primero.
 */
class SavedItineraries(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun newId(): String = UUID.randomUUID().toString()

    /** Guarda [proposal] como [id]: si ya estaba —se cambió uno guardado—, lo sustituye y sube arriba. */
    fun save(id: String, proposal: ItineraryProposal) {
        val entry = JSONObject()
            .put("id", id)
            .put("savedAt", System.currentTimeMillis())
            .put("pace", (proposal.request.pace ?: Pace.NORMAL).name)
            .put("mealAfter", proposal.mealAfter ?: -1)
            .put("stays", JSONArray(proposal.stays))
            .put("stops", JSONArray(proposal.stops.map(::placeJson)))
        val rest = entries().filter { it.optString("id") != id }
        write(listOf(entry) + rest)
    }

    fun delete(id: String) = write(entries().filter { it.optString("id") != id })

    /**
     * Los guardados, con las horas rehechas para salir **ahora** desde [here]:
     * las de cuando se armaron ya no valen.
     */
    fun all(here: Coordinates, nowMinute: Int): List<SavedItinerary> = entries().mapNotNull { json ->
        runCatching {
            val stops = json.getJSONArray("stops").let { array -> (0 until array.length()).map { placeOf(array.getJSONObject(it)) } }
            if (stops.isEmpty()) return@mapNotNull null
            val stays = json.getJSONArray("stays").let { array -> (0 until array.length()).map { array.getInt(it) } }
            val pace = Pace.entries.firstOrNull { it.name == json.optString("pace") } ?: Pace.NORMAL
            val base = ItineraryProposal(
                stops = stops,
                minutes = 0,
                mealAfter = json.optInt("mealAfter", -1).takeIf { it >= 0 },
                request = ItineraryRequest(pace = pace),
                from = here,
                startMinute = nowMinute,
            )
            SavedItinerary(json.getString("id"), json.optLong("savedAt"), ItineraryDesigner.rebuild(base, stops, stays))
        }.getOrNull()
    }

    private fun entries(): List<JSONObject> {
        migrate()
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { JSONArray(raw).let { array -> (0 until array.length()).map { array.getJSONObject(it) } } }.getOrDefault(emptyList())
    }

    private fun write(entries: List<JSONObject>) {
        prefs.edit().putString(KEY, JSONArray(entries).toString()).apply()
    }

    /** Antes se guardaba uno solo: pasa a ser el primero de la lista. */
    private fun migrate() {
        val old = prefs.getString(OLD_KEY, null) ?: return
        val entry = runCatching { JSONObject(old).put("id", newId()).put("savedAt", System.currentTimeMillis()) }.getOrNull()
        prefs.edit().remove(OLD_KEY).putString(KEY, JSONArray(listOfNotNull(entry)).toString()).apply()
    }

    private fun placeJson(place: Place) = JSONObject()
        .put("id", place.id)
        .put("name", place.name)
        .put("lat", place.position.latitude)
        .put("lon", place.position.longitude)
        .put("seconds", place.narrationSeconds)
        .put("languages", JSONArray(place.languages.map { it.code }))
        .put("city", place.city)
        .put("image", place.imageUrl)
        .put("summary", place.summary)
        .put("contentLanguage", place.contentLanguage?.code)

    private fun placeOf(json: JSONObject) = Place(
        id = json.getString("id"),
        name = json.getString("name"),
        position = Coordinates(json.getDouble("lat"), json.getDouble("lon")),
        narrationSeconds = json.optInt("seconds", 60),
        languages = json.optJSONArray("languages")
            ?.let { array -> (0 until array.length()).mapNotNull { VoiceLanguage.fromTag(array.getString(it)) }.toSet() }
            .orEmpty(),
        city = json.optString("city").takeIf { it.isNotEmpty() && it != "null" },
        imageUrl = json.optString("image").takeIf { it.isNotEmpty() && it != "null" },
        summary = json.optString("summary").takeIf { it.isNotEmpty() && it != "null" },
        contentLanguage = VoiceLanguage.fromTag(json.optString("contentLanguage")),
    )

    private companion object {
        const val FILE = "saved_itinerary"
        const val KEY = "all"
        const val OLD_KEY = "latest"
    }
}
