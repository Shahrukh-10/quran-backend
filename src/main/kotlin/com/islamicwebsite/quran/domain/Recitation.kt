package com.islamicwebsite.quran.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.io.Serializable

@Document(collection = "qc_recitation")
class Recitation(
    @Id var id: Int = 0,
    var reciterName: String = "",
    var style: String? = null
)

/**
 * Composite id for AyahRecitation. Stored as a nested BSON object under _id.
 * Field names mirror the JPA @EmbeddedId shape so `findByKeyVerseKey`/`findByKeyRecitationId`
 * derived Mongo queries continue to work — Spring Data derives paths as key.verseKey → key.verseKey.
 */
data class AyahRecitationKey(
    var recitationId: Int = 0,
    var verseKey: String = ""
) : Serializable

@Document(collection = "qc_ayah_recitation")
class AyahRecitation(
    @Id var key: AyahRecitationKey = AyahRecitationKey(),
    var audioUrl: String = "",
    var segmentsJson: String = "[]"
)

data class ChapterRecitationKey(
    var recitationId: Int = 0,
    var chapterId: Int = 0
) : Serializable

@Document(collection = "qc_chapter_recitation")
class ChapterRecitation(
    @Id var key: ChapterRecitationKey = ChapterRecitationKey(),
    var audioUrl: String = "",
    var fileSize: Long = 0
)
