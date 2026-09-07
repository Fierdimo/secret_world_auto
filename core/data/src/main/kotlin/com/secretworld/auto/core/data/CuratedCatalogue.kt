package com.secretworld.auto.core.data

import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.Place
import com.secretworld.auto.domain.VoiceLanguage

/**
 * Catálogo de respaldo: **contenido real de Secret World**, capturado del propio
 * API, no inventado.
 *
 * Existe por una razón concreta: una demostración que se queda en blanco porque el
 * garaje no tiene cobertura no demuestra nada. Cuando la red falla o el API rechaza
 * al cliente, la app sigue teniendo algo que contar, y **lo dice**.
 *
 * No sustituye al catálogo: son 18 lugares de Roma frente a los miles que hay
 * detrás del API.
 */
object CuratedCatalogue {

    val places: List<Place> = listOf(
        seed(
            id = "57611",
            name = "Abbacchio alla Romana",
            latitude = 41.9027835,
            longitude = 12.4963655,
            city = "Roma",
            imageUrl = "https://sworld.co.uk/img/img/1071/57611///previews/0.jpeg",
            summary = "Abbacchioo is a young lamb that still feeds on milk. Its meat is very tender and traditionally cooked in Rome and the rest of Lazio.",
        ),
        seed(
            id = "48447",
            name = "Pancakes, from the Greeks to the U.S.",
            latitude = 41.9027835,
            longitude = 12.4963655,
            city = "Roma",
            imageUrl = "https://sworld.co.uk/img/img/125/photoAlbum/48447//previews/0.jpg",
            summary = "The pancake recipe has made a long journey, crossing continents and entire centuries to our tables, directly from the United States, but the delicious pancakes stuffed with maple syrup, jam, honey or dried fruit were not prepared there for the first time. In 500 B.C., in fact, Cratino and Magnete, two fellow playwrights of the more famous Aristophanes, mention a cake made with water, olive oil and flour, cooked and round, served with honey at breakfast. The Greeks called it teganites or tagenites, referring to the type of pan in which they were prepared, but we can to all intents and purposes say (if only we could taste it!) that it is the ancestor of pancakes, although without yeast.",
        ),
        seed(
            id = "49822",
            name = "Rome",
            latitude = 41.9027835,
            longitude = 12.4963655,
            city = "Roma",
            imageUrl = "https://sworld.co.uk/img/img/117/photoAlbum/49822//previews/0.jpg",
            summary = "Rome, often referred to as the Eternal City, is a living canvas of history, culture, and art that stirs the soul. With ancient roots tracing back to its legendary founding in 753 BC by Romulus, the city has served as the epicenter of the Roman Empire, a cradle of Christianity, and a beacon of the Renaissance. Each cobblestone street and majestic structure tells a story, inviting visitors to step back in time and explore its rich tapestry. The historical significance of Rome is monumental. Once a small settlement on the banks of the Tiber River, it transformed into one of the most powerful cities in the ancient world.",
        ),
        seed(
            id = "76895",
            name = "Roman Pizza",
            latitude = 41.9027835,
            longitude = 12.4963655,
            city = "Roma",
            imageUrl = "https://sworld.co.uk/img/img/594/photoAlbum/76895//previews/0.jpg",
            summary = "In the heart of Rome, a city that echoes with the whispers of ancient legends and the grandeur of its imperial past, there lies a culinary tradition as rich and enticing as its history: Pizza Romana. This delightful dish, with its simple yet flavorful combination of tomato, salted anchovies, oregano, capers, and oil, offers more than just a taste—it serves a slice of Roman history. The story of pizza in Rome intertwines with the city's vibrant history, dating back to the ancient Roman Empire. While modern pizza as we know it originated in Naples, the Romans had their own flatbread versions, often topped with olive oil and herbs, that can be seen as precursors to today's pizzas.",
        ),
        seed(
            id = "136173",
            name = "Rome, the city with a thousand faces",
            latitude = 41.9027835,
            longitude = 12.4963655,
            city = "Roma",
            imageUrl = "https://sworld.co.uk/img/img/567/photoAlbum/136173//previews/0.jpg",
            summary = "To discover it all would take months, if not years, but visiting Rome in 3 days is still enough to give you an idea of the incredible beauty of the eternal city. Monuments and attractions await you around every corner, every step is a surprise that does not disappoint. The largest and most amazing open-air museum in the world. This is Rome, the capital of Italy, the first great metropolis of humanity and one of the largest cities in Europe. Rome condenses its three millennia of history into breath-taking architectural and artistic testimonies, masterpieces that make it one of the most visited and admired cities in the world.",
        ),
        seed(
            id = "535867",
            name = "The Maritozzi are soft paninidolci stuffed with whipped cream, delicious do",
            latitude = 41.9027835,
            longitude = 12.4963655,
            city = "Roma",
            imageUrl = "https://sworld.co.uk/img/img/725/photoAlbum/535867//previews/0.jpeg",
            summary = "Maritozzi are not just a culinary delight in Rome; they are a sweet symbol of tradition, history, and culture that have persisted through centuries. These soft, sweet rolls, typically filled with rich, whipped cream, are an essential part of the Roman breakfast and snack culture, served in every bar and pastry shop throughout the capital and the surrounding Lazio region. History and Origins The origins of maritozzi trace back to ancient Rome, where they were first created as a form of a gift from suitors to their future brides. The term “maritozzo” derives from the popular Roman word for “husband,” reflecting the cake's significance as a token of affection.",
        ),
        seed(
            id = "769487",
            name = "The Doors of Igor Mitoraj",
            latitude = 41.9031888,
            longitude = 12.4969341,
            city = "Roma",
            imageUrl = "https://sworld.co.uk/img/img/604/photoAlbum/769487//previews/0.jpg",
            summary = "The Doors of Igor Mitoraj Height 6.5 m Width 3 m. Weight 3 tons. Right door: ANNOUNCEMENT It has as its theme the mystery of the Annunciation of the Angel to the Virgin Mary: an angel from above (left wing) and Mary listening (right wing). A diffuse background gives a glimpse of the space of the world. The earthly orb unfolds, through glazed backgrounds and rippling drafts that are intended to signify the transformation of chronological time into a time of grace. On the lunette, in a sober and scattered way, some figures highlight the presence of the Angels: figures of only heads, sometimes blindfolded and a young body without the head.",
        ),
        seed(
            id = "5051",
            name = "Palazzo Massimo, a branch of the National Museum of Rome",
            latitude = 41.9013587,
            longitude = 12.498255400000062,
            city = "Roma",
            imageUrl = "https://sworld.co.uk/img/img/85/photoAlbum/5051//previews/0.JPG",
            summary = "Palazzo Massimo is a jewel within the rich tapestry of Rome, Italy, serving as one of the four branches of the National Museum of Rome. Nestled near the bustling Termini Station, this museum is a treasure trove of artifacts that narrate the story of ancient Rome and its profound influence on modern civilization. History and Origins The origins of Palazzo Massimo date back to the 19th century when it was established in a historic building constructed in the late 1800s. This palazzo, once a residence for the Massimo family, reflects the architectural grandeur typical of Roman nobility.",
        ),
        seed(
            id = "21960",
            name = "National Museum of Palazzo Massimo alle Terme",
            latitude = 41.9013587,
            longitude = 12.498255400000062,
            city = "Roma",
            imageUrl = "https://sworld.co.uk/img/img/170/photoAlbum/21960//previews/0.jpg",
            summary = "Nestled in the heart of Rome, the National Museum of Palazzo Massimo alle Terme offers a captivating journey through ancient art and history. Originally constructed as a Jesuit boarding school between 1883 and 1886 by architect Camillo Castrucci, this stunning palace has a rich narrative that intertwines with the fabric of the city. After various ownership changes, the Italian state acquired the property in 1981, leading to a comprehensive restoration that culminated in the museum's inauguration in 1995.",
        ),
        seed(
            id = "769548",
            name = "Ecstasy of St. Teresa",
            latitude = 41.90470079999999,
            longitude = 12.4943693,
            city = "Roma",
            imageUrl = "https://sworld.co.uk/img/img/1200/photoAlbum/769548//previews/0.jpg",
            summary = "In the heart of Rome, a city steeped in history and artistry, lies a hidden gem that encapsulates the divine interplay of spirituality and creativity. Santa Maria della Vittoria, a Baroque church located at Via XX Settembre, is home to one of the most breathtaking masterpieces of the Italian Renaissance: the Ecstasy of St. Teresa. History and Origins The church was commissioned in 1605 by the Discalced Carmelite Order, founded by Saint Teresa of Avila herself, a pivotal figure in the Catholic Reformation. Construction began shortly after its founding and continued until its completion in 1620.",
        ),
        seed(
            id = "57863",
            name = "Church of St. Mary of Victory",
            latitude = 41.9047137,
            longitude = 12.4942803,
            city = "Roma",
            imageUrl = "https://sworld.co.uk/img/img/1363/57863///previews/0.jpg",
            summary = "Initially dedicated to St. Paul and built for the Discalced Carmelites between 1608 and 1620, the church was renamed on the occasion of the Battle of White Mountain (near Prague) in the War...",
        ),
        seed(
            id = "57867",
            name = "Estates of Santa Teresa, (1647-1651)",
            latitude = 41.9047137,
            longitude = 12.4942803,
            city = "Roma",
            imageUrl = "https://sworld.co.uk/img/img/963/57867///previews/0.jpg",
            summary = "Considered one of Bernini's masterpieces, the work depicts the Transverberation of Saint Theresa, made for the Cornaro Chapel in Santa Maria della Vittoria in Rome.",
        ),
        seed(
            id = "1949",
            name = "Rome:St. Mary of the Victory, a small baroque church",
            latitude = 41.9047137,
            longitude = 12.4942803,
            city = "Roma",
            imageUrl = "https://sworld.co.uk/img/img/59/photoAlbum/1949//previews/0.jpg",
            summary = "In the heart of Rome, where history and art breathe life into every corner, stands the Santa Maria della Vittoria, a small yet profoundly significant baroque basilica. Nestled on Via XX Settembre, this church might be modest in size, but it boasts an opulent interior that has captivated visitors for centuries. The church's origins date back to the early 17th century. It was initially constructed for the Discalced Carmelites and dedicated to Saint Paul. However, its dedication shifted following the Catholic victory at the Battle of White Mountain in 1620, a pivotal event during the Thirty Years' War. In gratitude for this triumph, the church was renamed Santa Maria della Vittoria.",
        ),
        seed(
            id = "793970",
            name = "Judith and Holofernes",
            latitude = 41.9031491,
            longitude = 12.4900479,
            city = "Roma",
            imageUrl = "https://sworld.co.uk/img/img/794/photoAlbum/793970//previews/0.jpg",
            summary = "In the heart of Rome, at the National Gallery of Ancient Art, lies one of Caravaggio's most haunting masterpieces: Judith and Holofernes. Completed in 1599, this evocative oil painting encapsulates both the violence and the profound emotional depth characteristic of the artist's work. As you stand before this iconic piece, you are drawn into a narrative steeped in ancient history and cultural significance that reverberates through the ages. The story of Judith and Holofernes is rooted in the Book of Judith, a deuterocanonical text that tells of Judith, a widowed Israelite, who saves her people from the invading Assyrian general, Holofernes.",
        ),
        seed(
            id = "57795",
            name = "St. Francis in meditation",
            latitude = 41.9025526,
            longitude = 12.4897398000001,
            city = "Roma",
            imageUrl = "https://sworld.co.uk/img/img/817/57795///previews/0.jpg",
            summary = "Saint Francis in Meditation is the subject of a beautiful painting made in 1605 by Italian painter Michelangelo Merisi da Caravaggio.",
        ),
        seed(
            id = "92557",
            name = "Palazzo Barberini and the National Gallery of Ancient Art",
            latitude = 41.9025526,
            longitude = 12.4897398000001,
            city = "Roma",
            imageUrl = "https://sworld.co.uk/img/img/583/photoAlbum/92557//previews/0.jpg",
            summary = "Nestled in the heart of Roma, the Palazzo Barberini is a baroque masterpiece that stands as a testament to the grandeur and artistic ambition of its era. This splendid structure, commissioned by Pope Urban VIII and designed by the renowned architect Carlo Maderno, is home to the illustrious National Gallery of Ancient Art. With its ornate architecture and an extraordinary collection of artworks, Palazzo Barberini offers a window into the opulence of 17th-century Roman nobility. The origins of Palazzo Barberini trace back to 1623 when Maffeo Barberini ascended to the papacy as Urban VIII.",
        ),
        seed(
            id = "262500",
            name = "The Fountain or Happy Water Exhibition",
            latitude = 41.9025526,
            longitude = 12.4897398000001,
            city = "Roma",
            imageUrl = "https://sworld.co.uk/img/img/659/photoAlbum/262500//previews/0.jpg",
            summary = "In the heart of Rome, where history whispers through cobblestone streets and every corner holds a story, stands the Fountain of Moses, also known as the Mostra dell'Acqua Felice. This monumental fountain is not just a testament to Roman engineering and artistry but a symbol of a city's ancient quest to harness and celebrate the life-giving force of water. History and Origins The fountain was commissioned by Pope Sixtus V in the late 16th century, during a time of fervent architectural development in Rome.",
        ),
        seed(
            id = "261969",
            name = "The Fountain of Bees by Gian Lorenzo Bernini",
            latitude = 41.90378459999999,
            longitude = 12.488638100000005,
            city = "Roma",
            imageUrl = "https://sworld.co.uk/img/img/635/photoAlbum/261969//previews/0.jpg",
            summary = "Nestled in the heart of Rome, just a stone's throw from the Triton Fountain, lies the enchanting Fountain of the Bees—a masterpiece crafted by the celebrated Gian Lorenzo Bernini in 1644. This striking public fountain, with its charming design and rich historical significance, is a testament to Bernini's artistic genius and the cultural tapestry of the city. History and Origins The Fountain of the Bees stands as a symbol of the Papacy during a period when artistic patronage flourished under Pope Urban VIII, the pontiff who commissioned the work. This was a time of immense transformation in Rome, where the Baroque style began to dominate.",
        ),
    )

    private fun seed(
        id: String,
        name: String,
        latitude: Double,
        longitude: Double,
        city: String,
        imageUrl: String,
        summary: String,
    ) = Place(
        id = id,
        name = name,
        position = Coordinates(latitude, longitude),
        narrationSeconds = (summary.split(" ").size * 60 / 150).coerceIn(20, 180),
        languages = setOf(VoiceLanguage.ENGLISH),
        city = city,
        imageUrl = imageUrl.ifBlank { null },
        summary = summary,
    )
}
