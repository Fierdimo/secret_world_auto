package com.secretworld.auto

import android.content.Context
import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.ItineraryDesigner
import com.secretworld.auto.domain.ItineraryProposal
import com.secretworld.auto.domain.ItineraryRequest
import com.secretworld.auto.domain.Pace
import com.secretworld.auto.domain.Place
import com.secretworld.auto.domain.VoiceLanguage
import org.json.JSONArray
import org.json.JSONObject

/**
 * El itinerario confirmado, guardado en el dispositivo: confirmar no es
 * arrancar, y se puede armar en el hotel y empezarlo después ("empieza mi
 * itinerario"). Uno solo, el último: es lo que se pide en un coche.
 */
class SavedItineraries(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun save(proposal: ItineraryProposal) {
        val json = JSONObject()
            .put("pace", (proposal.request.pace ?: Pace.NORMAL).name)
            .put("mealAfter", proposal.mealAfter ?: -1)
            .put("stays", JSONArray(proposal.stays))
            .put("stops", JSONArray(proposal.stops.map(::placeJson)))
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    /**
     * El guardado, con las horas rehechas para salir **ahora** desde [here]:
     * las de cuando se armó ya no valen.
     */
    fun load(here: Coordinates, nowMinute: Int): ItineraryProposal? {
        val raw = prefs.getString(KEY, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val stops = json.getJSONArray("stops").let { array -> (0 until array.length()).map { placeOf(array.getJSONObject(it)) } }
            if (stops.isEmpty()) return null
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
            ItineraryDesigner.rebuild(base, stops, stays)
        }.getOrNull()
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
        const val KEY = "latest"
    }
}
