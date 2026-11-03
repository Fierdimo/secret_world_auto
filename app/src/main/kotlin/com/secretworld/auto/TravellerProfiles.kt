package com.secretworld.auto

import android.content.Context
import com.secretworld.auto.domain.Interest
import com.secretworld.auto.domain.TravellerProfile

/**
 * El perfil para armar itinerarios, guardado **solo en el dispositivo** (SPEC
 * RF-8.5). Sincronizarlo con la cuenta queda para RF-5.
 */
class TravellerProfiles(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): TravellerProfile = TravellerProfile(
        likes = interests(LIKES),
        dislikes = interests(DISLIKES),
        visited = prefs.getStringSet(VISITED, emptySet()).orEmpty(),
    )

    fun save(profile: TravellerProfile) {
        prefs.edit()
            .putStringSet(LIKES, profile.likes.mapTo(HashSet()) { it.name })
            .putStringSet(DISLIKES, profile.dislikes.mapTo(HashSet()) { it.name })
            .putStringSet(VISITED, HashSet(profile.visited))
            .apply()
    }

    fun update(change: (TravellerProfile) -> TravellerProfile) = save(change(load()))

    /** "Olvida mis preferencias" (RF-8.6). */
    fun clear() {
        prefs.edit().clear().apply()
    }

    /** Un nombre que ya no exista —se renombró un interés— se ignora, no rompe. */
    private fun interests(key: String): Set<Interest> =
        prefs.getStringSet(key, emptySet()).orEmpty()
            .mapNotNullTo(HashSet()) { name -> Interest.entries.firstOrNull { it.name == name } }

    private companion object {
        const val FILE = "traveller_profile"
        const val LIKES = "likes"
        const val DISLIKES = "dislikes"
        const val VISITED = "visited"
    }
}
