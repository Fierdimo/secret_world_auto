package com.secretworld.auto.domain

/** Lo que hace falta para comparar texto que llega en otro alfabeto. */
internal object Script {

    /** Cuántas letras hay que cambiar para pasar de una palabra a otra. */
    fun editDistance(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            prev.indices.forEach { prev[it] = cur[it] }
        }
        return prev[b.length]
    }

    /**
     * El cirílico, en letras latinas. El reconocedor que entiende italiano
     * conoce también ruso, ucraniano y bielorruso, y al oír "Sícret" escribe a
     * veces «Секрет»: medido en un Huawei, 4 de 13 intentos. La palabra es la
     * buena; solo cambia el alfabeto. Lo usan la palabra de activación y la
     * gramática de órdenes.
     */
    fun latin(text: String): String = buildString {
        text.forEach { append(CYRILLIC[it] ?: it.toString()) }
    }

    private val CYRILLIC = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'ґ' to "g", 'д' to "d", 'е' to "e", 'ё' to "e",
        'є' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i", 'і' to "i", 'ї' to "i", 'й' to "i", 'к' to "k",
        'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
        'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "ch", 'ш' to "sh", 'щ' to "sh", 'ъ' to "",
        'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
    )
}
