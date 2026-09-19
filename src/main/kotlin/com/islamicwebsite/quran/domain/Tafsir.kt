package com.islamicwebsite.quran.domain

import jakarta.persistence.*
import java.io.Serializable

@Entity
@Table(name = "qc_tafsir")
class Tafsir(
    @Id var id: Int = 0,
    @Column(name = "name") var name: String = "",
    @Column(name = "author_name") var authorName: String = "",
    @Column(name = "slug") var slug: String = "",
    @Column(name = "language_name") var languageName: String = ""
)

@Embeddable
class TafsirVerseKey(
    @Column(name = "tafsir_id") var tafsirId: Int = 0,
    @Column(name = "verse_key") var verseKey: String = ""
) : Serializable {
    override fun equals(other: Any?): Boolean =
        this === other || (other is TafsirVerseKey && tafsirId == other.tafsirId && verseKey == other.verseKey)
    override fun hashCode(): Int = tafsirId * 31 + verseKey.hashCode()
}

@Entity
@Table(name = "qc_tafsir_verse")
class TafsirVerse(
    @EmbeddedId var key: TafsirVerseKey = TafsirVerseKey(),
    @Column(name = "text", columnDefinition = "CLOB") var text: String = ""
)
