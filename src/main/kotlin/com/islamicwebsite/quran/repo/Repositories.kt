package com.islamicwebsite.quran.repo

import com.islamicwebsite.quran.domain.*
import org.springframework.data.jpa.repository.JpaRepository

interface ChapterRepository : JpaRepository<Chapter, Int>

interface VerseRepository : JpaRepository<Verse, String> {
    fun findByChapterIdOrderByVerseNumberAsc(chapterId: Int): List<Verse>
    fun findByJuzNumber(juzNumber: Int): List<Verse>
    fun findByPageNumber(pageNumber: Int): List<Verse>
}

interface WordRepository : JpaRepository<Word, Long> {
    fun findByVerseKeyOrderByPositionAsc(verseKey: String): List<Word>
}

interface TranslationRepository : JpaRepository<Translation, Int>

interface TranslationVerseRepository : JpaRepository<TranslationVerse, TranslationVerseKey> {
    fun findByKeyVerseKey(verseKey: String): List<TranslationVerse>
    fun findByKeyTranslationId(translationId: Int): List<TranslationVerse>
}

interface TafsirRepository : JpaRepository<Tafsir, Int>

interface TafsirVerseRepository : JpaRepository<TafsirVerse, TafsirVerseKey> {
    fun findByKeyVerseKey(verseKey: String): List<TafsirVerse>
}

interface RecitationRepository : JpaRepository<Recitation, Int>

interface AyahRecitationRepository : JpaRepository<AyahRecitation, AyahRecitationKey> {
    fun findByKeyVerseKey(verseKey: String): List<AyahRecitation>
}

interface ChapterRecitationRepository : JpaRepository<ChapterRecitation, ChapterRecitationKey>

interface SyncStateRepository : JpaRepository<SyncState, String>
