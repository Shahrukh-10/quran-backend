package com.islamicwebsite.quran.domain

import jakarta.persistence.*

@Entity
@Table(name = "qc_chapter")
class Chapter(
    @Id var id: Int = 0,
    @Column(name = "revelation_place") var revelationPlace: String = "",
    @Column(name = "revelation_order") var revelationOrder: Int = 0,
    @Column(name = "bismillah_pre") var bismillahPre: Boolean = false,
    @Column(name = "name_simple") var nameSimple: String = "",
    @Column(name = "name_complex") var nameComplex: String = "",
    @Column(name = "name_arabic") var nameArabic: String = "",
    @Column(name = "verses_count") var versesCount: Int = 0,
    @Column(name = "page_start") var pageStart: Int = 0,
    @Column(name = "page_end") var pageEnd: Int = 0,
    @Column(name = "translated_name") var translatedName: String = ""
)

@Entity
@Table(name = "qc_verse")
class Verse(
    @Id
    @Column(name = "verse_key") var verseKey: String = "",
    @Column(name = "chapter_id") var chapterId: Int = 0,
    @Column(name = "verse_number") var verseNumber: Int = 0,
    @Column(name = "juz_number") var juzNumber: Int = 0,
    @Column(name = "hizb_number") var hizbNumber: Int = 0,
    @Column(name = "rub_el_hizb_number") var rubElHizbNumber: Int? = null,
    @Column(name = "ruku_number") var rukuNumber: Int = 0,
    @Column(name = "manzil_number") var manzilNumber: Int = 0,
    @Column(name = "page_number") var pageNumber: Int = 0,
    @Column(name = "sajdah_number") var sajdahNumber: Int? = null,
    @Column(name = "text_uthmani", columnDefinition = "CLOB") var textUthmani: String = "",
    @Column(name = "text_indopak", columnDefinition = "CLOB") var textIndopak: String? = null,
    @Column(name = "text_imlaei", columnDefinition = "CLOB") var textImlaei: String? = null
)

@Entity
@Table(name = "qc_word")
class Word(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @Column(name = "verse_key") var verseKey: String = "",
    @Column(name = "position") var position: Int = 0,
    @Column(name = "char_type_name") var charTypeName: String = "",
    @Column(name = "text_uthmani") var textUthmani: String = "",
    @Column(name = "text_indopak") var textIndopak: String? = null,
    @Column(name = "translation") var translation: String? = null,
    @Column(name = "transliteration") var transliteration: String? = null,
    @Column(name = "audio_relative_url") var audioRelativeUrl: String? = null,
    @Column(name = "page_number") var pageNumber: Int = 0,
    @Column(name = "line_number") var lineNumber: Int = 0
)
