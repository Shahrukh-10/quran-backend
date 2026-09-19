package com.islamicwebsite.quran.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.islamicwebsite.quran.domain.*
import com.islamicwebsite.quran.repo.*
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class PublicApi(
    private val chapters: ChapterRepository,
    private val verses: VerseRepository,
    private val words: WordRepository,
    private val translations: TranslationRepository,
    private val translationVerses: TranslationVerseRepository,
    private val tafsirs: TafsirRepository,
    private val tafsirVerses: TafsirVerseRepository,
    private val recitations: RecitationRepository,
    private val ayahRecitations: AyahRecitationRepository,
    private val chapterRecitations: ChapterRecitationRepository,
    private val mapper: ObjectMapper,
) {
    @GetMapping("/chapters")
    @Cacheable("chapters")
    fun listChapters(): List<Chapter> = chapters.findAll().sortedBy { it.id }

    @GetMapping("/chapters/{id}")
    fun getChapter(@PathVariable id: Int): ResponseEntity<Chapter> =
        chapters.findById(id).map { ResponseEntity.ok(it) }.orElse(ResponseEntity.notFound().build())

    @GetMapping("/chapters/{id}/verses")
    fun chapterVerses(@PathVariable id: Int): List<Map<String, Any?>> {
        val verseRows = verses.findByChapterIdOrderByVerseNumberAsc(id)
        return verseRows.map { v ->
            mapOf(
                "verse_key" to v.verseKey,
                "verse_number" to v.verseNumber,
                "juz_number" to v.juzNumber,
                "hizb_number" to v.hizbNumber,
                "ruku_number" to v.rukuNumber,
                "manzil_number" to v.manzilNumber,
                "page_number" to v.pageNumber,
                "text_uthmani" to v.textUthmani,
                "text_indopak" to v.textIndopak,
                "text_imlaei" to v.textImlaei,
            )
        }
    }

    @GetMapping("/verses/{key}")
    fun getVerse(@PathVariable key: String): ResponseEntity<Map<String, Any?>> {
        val v = verses.findById(key).orElse(null) ?: return ResponseEntity.notFound().build()
        val ws = words.findByVerseKeyOrderByPositionAsc(key).map { w ->
            mapOf(
                "position" to w.position,
                "char_type_name" to w.charTypeName,
                "text_uthmani" to w.textUthmani,
                "text_indopak" to w.textIndopak,
                "translation" to w.translation,
                "transliteration" to w.transliteration,
                "audio_relative_url" to w.audioRelativeUrl,
            )
        }
        return ResponseEntity.ok(
            mapOf(
                "verse_key" to v.verseKey,
                "chapter_id" to v.chapterId,
                "verse_number" to v.verseNumber,
                "juz_number" to v.juzNumber,
                "hizb_number" to v.hizbNumber,
                "ruku_number" to v.rukuNumber,
                "manzil_number" to v.manzilNumber,
                "page_number" to v.pageNumber,
                "sajdah_number" to v.sajdahNumber,
                "text_uthmani" to v.textUthmani,
                "text_indopak" to v.textIndopak,
                "text_imlaei" to v.textImlaei,
                "words" to ws,
            )
        )
    }

    @GetMapping("/translations")
    fun listTranslations(): List<Translation> = translations.findAll().sortedBy { it.id }

    @GetMapping("/translations/{id}/by-chapter/{chapter}")
    fun translationByChapter(@PathVariable id: Int, @PathVariable chapter: Int): List<Map<String, String>> {
        val chapterVerses = verses.findByChapterIdOrderByVerseNumberAsc(chapter).map { it.verseKey }.toSet()
        return translationVerses.findByKeyTranslationId(id)
            .filter { it.key.verseKey in chapterVerses }
            .sortedBy { it.key.verseKey.split(":")[1].toInt() }
            .map { mapOf("verse_key" to it.key.verseKey, "text" to it.text) }
    }

    @GetMapping("/tafsirs")
    fun listTafsirs(): List<Tafsir> = tafsirs.findAll().sortedBy { it.id }

    @GetMapping("/tafsirs/{id}/by-verse/{key}")
    fun tafsirByVerse(@PathVariable id: Int, @PathVariable key: String): ResponseEntity<Map<String, String>> {
        val rows = tafsirVerses.findByKeyVerseKey(key).filter { it.key.tafsirId == id }
        val row = rows.firstOrNull() ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("verse_key" to row.key.verseKey, "text" to row.text))
    }

    @GetMapping("/recitations")
    fun listRecitations(): List<Recitation> = recitations.findAll().sortedBy { it.id }

    @GetMapping("/recitations/{id}/by-chapter/{chapter}")
    fun recitationByChapter(@PathVariable id: Int, @PathVariable chapter: Int): List<Map<String, Any?>> {
        val chapterVerses = verses.findByChapterIdOrderByVerseNumberAsc(chapter).map { it.verseKey }.toSet()
        return ayahRecitations.findAll()
            .filter { it.key.recitationId == id && it.key.verseKey in chapterVerses }
            .sortedBy { it.key.verseKey.split(":")[1].toInt() }
            .map { r ->
                val segments = try { mapper.readTree(r.segmentsJson) } catch (_: Throwable) { mapper.createArrayNode() }
                mapOf("verse_key" to r.key.verseKey, "audio_url" to r.audioUrl, "segments" to segments)
            }
    }

    @GetMapping("/search")
    fun search(@RequestParam q: String, @RequestParam(defaultValue = "50") limit: Int): List<Map<String, Any?>> {
        val query = q.trim()
        if (query.isEmpty()) return emptyList()
        val capped = minOf(limit, 200)
        // Reference lookup e.g. "2:255"
        val ref = Regex("^(\\d{1,3}):(\\d{1,3})$").find(query)
        if (ref != null) {
            val s = ref.groupValues[1].toInt(); val a = ref.groupValues[2].toInt()
            val v = verses.findById("$s:$a").orElse(null)
            if (v != null) return listOf(mapOf("verse_key" to v.verseKey, "snippet" to "Verse reference", "field" to "reference"))
        }
        // Simple LIKE on translation text for Sahih Intl (id 20)
        return translationVerses.findAll()
            .filter { it.key.translationId == 20 && it.text.contains(query, ignoreCase = true) }
            .take(capped)
            .map { mapOf("verse_key" to it.key.verseKey, "snippet" to it.text.take(200), "field" to "translation") }
    }
}
