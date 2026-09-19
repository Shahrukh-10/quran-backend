package com.islamicwebsite.quran.domain

import jakarta.persistence.*
import java.io.Serializable

@Entity
@Table(name = "qc_recitation")
class Recitation(
    @Id var id: Int = 0,
    @Column(name = "reciter_name") var reciterName: String = "",
    @Column(name = "style") var style: String? = null
)

@Embeddable
class AyahRecitationKey(
    @Column(name = "recitation_id") var recitationId: Int = 0,
    @Column(name = "verse_key") var verseKey: String = ""
) : Serializable {
    override fun equals(other: Any?): Boolean =
        this === other || (other is AyahRecitationKey && recitationId == other.recitationId && verseKey == other.verseKey)
    override fun hashCode(): Int = recitationId * 31 + verseKey.hashCode()
}

@Entity
@Table(name = "qc_ayah_recitation")
class AyahRecitation(
    @EmbeddedId var key: AyahRecitationKey = AyahRecitationKey(),
    @Column(name = "audio_url") var audioUrl: String = "",
    @Column(name = "segments", columnDefinition = "CLOB") var segmentsJson: String = "[]"
)

@Embeddable
class ChapterRecitationKey(
    @Column(name = "recitation_id") var recitationId: Int = 0,
    @Column(name = "chapter_id") var chapterId: Int = 0
) : Serializable {
    override fun equals(other: Any?): Boolean =
        this === other || (other is ChapterRecitationKey && recitationId == other.recitationId && chapterId == other.chapterId)
    override fun hashCode(): Int = recitationId * 31 + chapterId
}

@Entity
@Table(name = "qc_chapter_recitation")
class ChapterRecitation(
    @EmbeddedId var key: ChapterRecitationKey = ChapterRecitationKey(),
    @Column(name = "audio_url") var audioUrl: String = "",
    @Column(name = "file_size") var fileSize: Long = 0
)
