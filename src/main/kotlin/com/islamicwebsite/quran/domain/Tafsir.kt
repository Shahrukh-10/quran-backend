package com.islamicwebsite.quran.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.io.Serializable

@Document(collection = "qc_tafsir")
class Tafsir(
    @Id var id: Int = 0,
    var name: String = "",
    var authorName: String = "",
    var slug: String = "",
    var languageName: String = ""
)

data class TafsirVerseKey(
    var tafsirId: Int = 0,
    var verseKey: String = ""
) : Serializable

@Document(collection = "qc_tafsir_verse")
class TafsirVerse(
    @Id var key: TafsirVerseKey = TafsirVerseKey(),
    var text: String = ""
)
