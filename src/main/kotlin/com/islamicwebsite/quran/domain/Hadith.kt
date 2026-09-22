package com.islamicwebsite.quran.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "qh_book")
class HadithBookEntity(
    @Id var slug: String = "",
    var apiSlug: String = "",
    var nameEn: String = "",
    var nameId: String = "",
    var arabicName: String = "",
    var compilerEn: String = "",
    var compilerId: String = "",
    var compilerArabic: String = "",
    var eraCe: String = "",
    var totalHadith: Int = 0,
    var displayOrder: Int = 0,
    var descriptionEn: String = "",
    var descriptionId: String = ""
)

@Document(collection = "qh_hadith")
class HadithEntity(
    @Id var id: Long = 0,
    var bookSlug: String = "",
    var number: String = "",
    var ordinal: Int = 0,
    var section: Int = 0,
    var arabic: String = "",
    var textEn: String = "",
    var textId: String = "",
    var grade: String? = null
)
