package com.islamicwebsite.quran.domain

import jakarta.persistence.*
import java.io.Serializable

@Entity
@Table(name = "qc_translation")
class Translation(
    @Id var id: Int = 0,
    @Column(name = "name") var name: String = "",
    @Column(name = "author_name") var authorName: String = "",
    @Column(name = "slug") var slug: String = "",
    @Column(name = "language_name") var languageName: String = ""
)

@Embeddable
class TranslationVerseKey(
    @Column(name = "translation_id") var translationId: Int = 0,
    @Column(name = "verse_key") var verseKey: String = ""
) : Serializable {
    override fun equals(other: Any?): Boolean =
        this === other || (other is TranslationVerseKey && translationId == other.translationId && verseKey == other.verseKey)
    override fun hashCode(): Int = translationId * 31 + verseKey.hashCode()
}

@Entity
@Table(name = "qc_translation_verse")
class TranslationVerse(
    @EmbeddedId var key: TranslationVerseKey = TranslationVerseKey(),
    @Column(name = "text", columnDefinition = "CLOB") var text: String = ""
)
