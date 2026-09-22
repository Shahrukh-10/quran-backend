package com.islamicwebsite.quran.repo

import com.islamicwebsite.quran.domain.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query

interface ChapterRepository : MongoRepository<Chapter, Int>

interface VerseRepository : MongoRepository<Verse, String> {
    fun findByChapterIdOrderByVerseNumberAsc(chapterId: Int): List<Verse>
    fun findByJuzNumber(juzNumber: Int): List<Verse>
    fun findByPageNumber(pageNumber: Int): List<Verse>
}

interface WordRepository : MongoRepository<Word, Long> {
    fun findByVerseKeyOrderByPositionAsc(verseKey: String): List<Word>
}

interface TranslationRepository : MongoRepository<Translation, Int>

interface TranslationVerseRepository : MongoRepository<TranslationVerse, TranslationVerseKey> {
    fun findByKeyVerseKey(verseKey: String): List<TranslationVerse>
    fun findByKeyTranslationId(translationId: Int): List<TranslationVerse>
}

interface TafsirRepository : MongoRepository<Tafsir, Int>

interface TafsirVerseRepository : MongoRepository<TafsirVerse, TafsirVerseKey> {
    fun findByKeyVerseKey(verseKey: String): List<TafsirVerse>
}

interface RecitationRepository : MongoRepository<Recitation, Int>

interface AyahRecitationRepository : MongoRepository<AyahRecitation, AyahRecitationKey> {
    fun findByKeyVerseKey(verseKey: String): List<AyahRecitation>
}

interface ChapterRecitationRepository : MongoRepository<ChapterRecitation, ChapterRecitationKey>

interface SyncStateRepository : MongoRepository<SyncState, String>

// -------- V2: Iqamah / Masjid --------

interface MasjidRepository : MongoRepository<Masjid, Long> {
    fun findBySlug(slug: String): Masjid?
    fun findByStatusOrderByCityAsc(status: String): List<Masjid>
    fun findByStatusOrderByCreatedAtDesc(status: String): List<Masjid>
    fun findByCountryAndStatus(country: String, status: String): List<Masjid>
    fun existsBySlug(slug: String): Boolean
}

// -------- V3: User Accounts + Sync --------

interface AppUserRepository : MongoRepository<AppUser, Long> {
    fun findByUsername(username: String): AppUser?
    fun existsByUsername(username: String): Boolean
}

interface UserSessionRepository : MongoRepository<UserSession, String> {
    fun findByToken(token: String): UserSession?
    // Derived-name delete — Spring Data Mongo generates a remove operation.
    fun deleteByToken(token: String)
    // Raw Mongo query: delete every session whose expiry has passed. Returns Long delete count.
    @Query(value = "{ 'expiresAt': { \$lt: ?0 } }", delete = true)
    fun deleteExpired(now: Long): Long
}

interface UserStateRepository : MongoRepository<UserState, Long>

// -------- V4: Hadith (Kutub as-Sittah) --------

interface HadithBookRepository : MongoRepository<HadithBookEntity, String> {
    fun findAllByOrderByDisplayOrderAsc(): List<HadithBookEntity>
}

interface HadithRepository : MongoRepository<HadithEntity, Long> {
    fun findByBookSlugAndNumber(bookSlug: String, number: String): HadithEntity?
    fun findByBookSlugOrderByOrdinalAsc(bookSlug: String, pageable: Pageable): Page<HadithEntity>
    fun countByBookSlug(bookSlug: String): Long
}
