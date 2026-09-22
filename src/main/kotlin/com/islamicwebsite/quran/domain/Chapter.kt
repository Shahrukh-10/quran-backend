package com.islamicwebsite.quran.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "qc_chapter")
class Chapter(
    @Id var id: Int = 0,
    var revelationPlace: String = "",
    var revelationOrder: Int = 0,
    var bismillahPre: Boolean = false,
    var nameSimple: String = "",
    var nameComplex: String = "",
    var nameArabic: String = "",
    var versesCount: Int = 0,
    var pageStart: Int = 0,
    var pageEnd: Int = 0,
    var translatedName: String = ""
)

@Document(collection = "qc_verse")
class Verse(
    @Id var verseKey: String = "",
    var chapterId: Int = 0,
    var verseNumber: Int = 0,
    var juzNumber: Int = 0,
    var hizbNumber: Int = 0,
    var rubElHizbNumber: Int? = null,
    var rukuNumber: Int = 0,
    var manzilNumber: Int = 0,
    var pageNumber: Int = 0,
    var sajdahNumber: Int? = null,
    var textUthmani: String = "",
    var textIndopak: String? = null,
    var textImlaei: String? = null
)

@Document(collection = "qc_word")
class Word(
    @Id var id: Long = 0,
    var verseKey: String = "",
    var position: Int = 0,
    var charTypeName: String = "",
    var textUthmani: String = "",
    var textIndopak: String? = null,
    var translation: String? = null,
    var transliteration: String? = null,
    var audioRelativeUrl: String? = null,
    var pageNumber: Int = 0,
    var lineNumber: Int = 0
)
