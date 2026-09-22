package com.islamicwebsite.quran.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.io.Serializable

@Document(collection = "qc_translation")
class Translation(
    @Id var id: Int = 0,
    var name: String = "",
    var authorName: String = "",
    var slug: String = "",
    var languageName: String = ""
)

data class TranslationVerseKey(
    var translationId: Int = 0,
    var verseKey: String = ""
) : Serializable

@Document(collection = "qc_translation_verse")
class TranslationVerse(
    @Id var key: TranslationVerseKey = TranslationVerseKey(),
    var text: String = ""
)
