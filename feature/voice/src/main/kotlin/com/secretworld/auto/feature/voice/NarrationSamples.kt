package com.secretworld.auto.feature.voice

import com.secretworld.auto.domain.VoiceLanguage

/**
 * Texto real de narracion, en el formato que pide SPEC RF-1.5: 60 a 90 segundos,
 * tono conversacional, escrito para ser oido. Mismo lugar en los dos idiomas para
 * poder comparar voces sin que cambie el contenido.
 *
 * El liston del spike 2 se juzga sobre esto, no sobre una frase suelta.
 */
object NarrationSamples {

    private val english = """
        You are driving along one of the oldest roads in the world. The Appian Way
        was begun in three hundred and twelve BC, and for centuries it was the main
        route between Rome and the south of Italy. The Romans called it the queen of
        roads, and they built it to last: a bed of gravel and mortar, topped with
        stone blocks fitted so tightly that a knife blade could not pass between
        them. Two thousand years later, long stretches of it are still here, and you
        can still see the ruts worn by cart wheels.
        What makes this road remarkable is not only the engineering. Along its edges
        the Romans buried their dead, because burial inside the city walls was
        forbidden. So the first miles out of Rome became a corridor of tombs and
        monuments, a kind of open air gallery for anyone arriving in the capital.
        Some of those tombs are still standing. If you look to your right in a few
        minutes, you will see a large round tower on a low hill. That is the tomb of
        Cecilia Metella, built for the daughter of a Roman consul, and later turned
        into a fortress by a medieval family who liked the view and the tolls they
        could charge. It has been standing there, more or less intact, since before
        the birth of Christ.
    """.trimIndent().replace("\n", " ")

    private val italian = """
        Stai percorrendo una delle strade piu antiche del mondo. La via Appia fu
        iniziata nel trecentododici avanti Cristo, e per secoli fu la via principale
        tra Roma e il sud d'Italia. I romani la chiamavano la regina delle strade, e
        la costruirono per durare: un letto di ghiaia e malta, coperto da blocchi di
        pietra incastrati cosi stretti che non ci passava la lama di un coltello.
        Duemila anni dopo, lunghi tratti sono ancora qui, e si vedono ancora i solchi
        lasciati dalle ruote dei carri.
        Cio che rende straordinaria questa strada non e soltanto l'ingegneria. Lungo
        i suoi bordi i romani seppellivano i loro morti, perche era vietato farlo
        dentro le mura della citta. Cosi le prime miglia fuori Roma divennero un
        corridoio di tombe e monumenti, una specie di galleria all'aperto per chi
        arrivava nella capitale.
        Alcune di quelle tombe sono ancora in piedi. Se guardi alla tua destra tra
        qualche minuto, vedrai una grande torre rotonda su una collina bassa. E la
        tomba di Cecilia Metella, costruita per la figlia di un console romano, e
        poi trasformata in fortezza da una famiglia medievale a cui piacevano il
        panorama e i pedaggi che poteva riscuotere. E li, piu o meno intatta, da
        prima della nascita di Cristo.
    """.trimIndent().replace("\n", " ")

    fun forLanguage(language: VoiceLanguage): String = when (language) {
        VoiceLanguage.ENGLISH -> english
        VoiceLanguage.ITALIAN -> italian
        VoiceLanguage.SPANISH -> spanish
    }

    /** La misma pieza, en español: el banco de pruebas está en Colombia. */
    val spanish: String = """
        A tu derecha se acerca la tumba de Cecilia Metella, uno de los monumentos
        más reconocibles de la antigua vía Apia. Es un gran cilindro de piedra,
        construido hace unos dos mil años para la hija de un cónsul, y convertido
        después en fortaleza por una familia medieval a la que le gustaban las
        vistas y los peajes que podía cobrar. Ahí sigue, casi intacta, desde antes
        del nacimiento de Cristo.
    """.trimIndent().replace("\n", " ")
}
