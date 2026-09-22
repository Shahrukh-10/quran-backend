package com.islamicwebsite.quran.sync

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.islamicwebsite.quran.domain.*
import com.islamicwebsite.quran.repo.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Seeds the constant Quran corpus (chapters, verses, words, translations,
 * tafsirs, recitations, audio timings, chapter recitations) into MongoDB
 * on first startup from the local ../quran-data JSON tree.
 *
 * Idempotent: if `qc_chapter` already holds 114 documents, we skip every
 * step. Otherwise we insert in bulk via MongoTemplate.insert(collection)
 * — the fastest path Spring Data exposes for large seed loads.
 *
 * Long-id documents (Word) are assigned a monotonically increasing id
 * inside this JVM before insertion since MongoDB does not generate Long ids.
 */
@Component
class BootstrapImporter(
    private val mongo: MongoTemplate,
    private val chapterRepo: ChapterRepository,
    private val verseRepo: VerseRepository,
    private val wordRepo: WordRepository,
    private val translationRepo: TranslationRepository,
    private val translationVerseRepo: TranslationVerseRepository,
    private val tafsirRepo: TafsirRepository,
    private val tafsirVerseRepo: TafsirVerseRepository,
    private val recitationRepo: RecitationRepository,
    private val ayahRecitationRepo: AyahRecitationRepository,
    private val chapterRecitationRepo: ChapterRecitationRepository,
    @Value("\${quran.data.root:../quran-data}") private val dataRoot: String,
) {
    private val log = LoggerFactory.getLogger(BootstrapImporter::class.java)
    private val mapper = ObjectMapper()
    private val wordIdSeq = AtomicLong(0)

    @EventListener(ApplicationReadyEvent::class)
    fun run() {
        val root = File(dataRoot).absoluteFile
        if (!root.exists()) {
            log.warn("quran.data.root not found: {}", root.absolutePath)
            return
        }
        log.info("BootstrapImporter starting; source={}", root.absolutePath)

        val existingChapters = chapterRepo.count()
        if (existingChapters >= 114L) {
            log.info("Chapters already imported ({}) — skipping", existingChapters)
        } else {
            // seed word-id sequence past any pre-existing rows so re-runs don't collide
            wordIdSeq.set(wordRepo.count() + 1)
            importChapters(File(root, "chapters.json"))
            importVerses(File(root, "verses"))
            importRecitations(File(root, "recitations.json"))
            importTranslations(File(root, "translations.json"), File(root, "translations"))
            importTafsirs(File(root, "tafsirs.json"), File(root, "tafsirs"))
            importAudioTimings(File(root, "audio-timings"))
            importChapterRecitations(File(root, "chapter-recitations"))
        }

        log.info(
            "BootstrapImporter complete. chapters={} verses={} translations={} tafsirs={} recitations={}",
            chapterRepo.count(), verseRepo.count(), translationRepo.count(), tafsirRepo.count(), recitationRepo.count()
        )
    }

    private fun importChapters(file: File) {
        if (!file.exists()) { log.warn("chapters.json missing: {}", file); return }
        val arr = mapper.readTree(file) as ArrayNode
        val docs = arr.map { row ->
            val pages = row.get("pages") as ArrayNode
            Chapter(
                id = row.get("id").asInt(),
                revelationPlace = row.get("revelation_place").asText(),
                revelationOrder = row.get("revelation_order").asInt(),
                bismillahPre = row.get("bismillah_pre").asBoolean(),
                nameSimple = row.get("name_simple").asText(),
                nameComplex = row.get("name_complex").asText(),
                nameArabic = row.get("name_arabic").asText(),
                versesCount = row.get("verses_count").asInt(),
                pageStart = pages.get(0).asInt(),
                pageEnd = pages.get(1).asInt(),
                translatedName = row.get("translated_name")?.get("name")?.asText() ?: "",
            )
        }
        mongo.insert(docs, Chapter::class.java)
        log.info("Imported {} chapters", docs.size)
    }

    private fun importVerses(dir: File) {
        if (!dir.exists()) { log.warn("verses/ dir missing: {}", dir); return }
        var verseCount = 0L
        var wordCount = 0L
        val verseBatch = mutableListOf<Verse>()
        val wordBatch = mutableListOf<Word>()

        fun flushVerses() {
            if (verseBatch.isNotEmpty()) {
                mongo.insert(verseBatch, Verse::class.java)
                verseCount += verseBatch.size
                verseBatch.clear()
            }
        }

        fun flushWords() {
            if (wordBatch.isNotEmpty()) {
                mongo.insert(wordBatch, Word::class.java)
                wordCount += wordBatch.size
                wordBatch.clear()
            }
        }

        for (s in 1..114) {
            val file = File(dir, "$s.json")
            if (!file.exists()) continue
            val arr = mapper.readTree(file) as ArrayNode
            for (v in arr) {
                verseBatch += Verse(
                    verseKey = v.get("verse_key").asText(),
                    chapterId = s,
                    verseNumber = v.get("verse_number").asInt(),
                    juzNumber = v.get("juz_number").asInt(),
                    hizbNumber = v.get("hizb_number").asInt(),
                    rubElHizbNumber = v.get("rub_el_hizb_number")?.takeUnless { it.isNull }?.asInt(),
                    rukuNumber = v.get("ruku_number").asInt(),
                    manzilNumber = v.get("manzil_number").asInt(),
                    pageNumber = v.get("page_number").asInt(),
                    sajdahNumber = v.get("sajdah_number")?.takeUnless { it.isNull }?.asInt(),
                    textUthmani = v.get("text_uthmani").asText(),
                    textIndopak = v.get("text_indopak")?.takeUnless { it.isNull }?.asText(),
                    textImlaei = v.get("text_imlaei")?.takeUnless { it.isNull }?.asText(),
                )
                val words = v.get("words") as? ArrayNode
                if (words != null) {
                    for (w in words) {
                        wordBatch += Word(
                            id = wordIdSeq.getAndIncrement(),
                            verseKey = v.get("verse_key").asText(),
                            position = w.get("position").asInt(),
                            charTypeName = w.get("char_type_name").asText(),
                            textUthmani = w.get("text_uthmani").asText(),
                            textIndopak = w.get("text_indopak")?.takeUnless { it.isNull }?.asText(),
                            translation = w.get("translation")?.get("text")?.asText(),
                            transliteration = w.get("transliteration")?.get("text")?.asText(),
                            audioRelativeUrl = w.get("audio_url")?.takeUnless { it.isNull }?.asText(),
                            pageNumber = w.get("page_number").asInt(),
                            lineNumber = w.get("line_number").asInt(),
                        )
                    }
                }
                if (wordBatch.size >= 1000) {
                    flushVerses(); flushWords()
                } else if (verseBatch.size >= 500) {
                    flushVerses()
                }
            }
        }
        flushVerses()
        flushWords()
        log.info("Imported {} verses, {} words", verseCount, wordCount)
    }

    private fun importRecitations(file: File) {
        if (!file.exists()) return
        val arr = mapper.readTree(file) as ArrayNode
        val docs = arr.map { row ->
            Recitation(
                id = row.get("id").asInt(),
                reciterName = row.get("reciter_name").asText(),
                style = row.get("style")?.takeUnless { it.isNull }?.asText(),
            )
        }
        mongo.insert(docs, Recitation::class.java)
        log.info("Imported {} recitations", docs.size)
    }

    private fun importTranslations(catalogFile: File, dir: File) {
        if (!catalogFile.exists() || !dir.exists()) return
        val catalog = mapper.readTree(catalogFile) as ArrayNode
        val syncedIds = dir.listFiles { f -> f.isDirectory && f.name.matches(Regex("\\d+")) }
            ?.map { it.name.toInt() }?.toSet() ?: emptySet()

        val catalogDocs = catalog.mapNotNull { t ->
            val id = t.get("id").asInt()
            if (id !in syncedIds) null else Translation(
                id = id,
                name = t.get("name").asText(),
                authorName = t.get("author_name").asText(),
                slug = t.get("slug").asText(),
                languageName = t.get("language_name").asText(),
            )
        }
        if (catalogDocs.isNotEmpty()) mongo.insert(catalogDocs, Translation::class.java)

        for (id in syncedIds) {
            val batch = mutableListOf<TranslationVerse>()
            var total = 0L
            for (s in 1..114) {
                val f = File(dir, "$id/$s.json"); if (!f.exists()) continue
                val rows = mapper.readTree(f) as ArrayNode
                for (r in rows) {
                    batch += TranslationVerse(
                        key = TranslationVerseKey(translationId = id, verseKey = r.get("verse_key").asText()),
                        text = r.get("text").asText(),
                    )
                }
                if (batch.size >= 2000) {
                    mongo.insert(batch, TranslationVerse::class.java)
                    total += batch.size; batch.clear()
                }
            }
            if (batch.isNotEmpty()) {
                mongo.insert(batch, TranslationVerse::class.java)
                total += batch.size
            }
            log.info("Imported translation {} ({} rows)", id, total)
        }
    }

    private fun importTafsirs(catalogFile: File, dir: File) {
        if (!catalogFile.exists() || !dir.exists()) return
        val catalog = mapper.readTree(catalogFile) as ArrayNode
        val syncedIds = dir.listFiles { f -> f.isDirectory && f.name.matches(Regex("\\d+")) }
            ?.map { it.name.toInt() }?.toSet() ?: emptySet()

        val catalogDocs = catalog.mapNotNull { t ->
            val id = t.get("id").asInt()
            if (id !in syncedIds) null else Tafsir(
                id = id,
                name = t.get("name").asText(),
                authorName = t.get("author_name").asText(),
                slug = t.get("slug").asText(),
                languageName = t.get("language_name").asText(),
            )
        }
        if (catalogDocs.isNotEmpty()) mongo.insert(catalogDocs, Tafsir::class.java)

        for (id in syncedIds) {
            val batch = mutableListOf<TafsirVerse>()
            var total = 0L
            for (s in 1..114) {
                val f = File(dir, "$id/$s.json"); if (!f.exists()) continue
                val rows = mapper.readTree(f) as ArrayNode
                for (r in rows) {
                    batch += TafsirVerse(
                        key = TafsirVerseKey(tafsirId = id, verseKey = r.get("verse_key").asText()),
                        text = r.get("text").asText(),
                    )
                }
                if (batch.size >= 500) {
                    mongo.insert(batch, TafsirVerse::class.java)
                    total += batch.size; batch.clear()
                }
            }
            if (batch.isNotEmpty()) {
                mongo.insert(batch, TafsirVerse::class.java)
                total += batch.size
            }
            log.info("Imported tafsir {} ({} rows)", id, total)
        }
    }

    private fun importAudioTimings(dir: File) {
        if (!dir.exists()) return
        val reciters = dir.listFiles { f -> f.isDirectory && f.name.matches(Regex("\\d+")) } ?: return
        for (rDir in reciters) {
            val rId = rDir.name.toInt()
            val batch = mutableListOf<AyahRecitation>()
            var total = 0L
            for (s in 1..114) {
                val f = File(rDir, "$s.json"); if (!f.exists()) continue
                val rows = mapper.readTree(f) as ArrayNode
                for (row in rows) {
                    val url = row.get("audio_url")?.takeUnless { it.isNull }?.asText() ?: continue
                    batch += AyahRecitation(
                        key = AyahRecitationKey(recitationId = rId, verseKey = row.get("verse_key").asText()),
                        audioUrl = url,
                        segmentsJson = mapper.writeValueAsString(row.get("segments")),
                    )
                }
                if (batch.size >= 2000) {
                    mongo.insert(batch, AyahRecitation::class.java)
                    total += batch.size; batch.clear()
                }
            }
            if (batch.isNotEmpty()) {
                mongo.insert(batch, AyahRecitation::class.java)
                total += batch.size
            }
            log.info("Imported audio timings for reciter {} ({} rows)", rId, total)
        }
    }

    private fun importChapterRecitations(dir: File) {
        if (!dir.exists()) return
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return
        for (f in files) {
            val rId = f.nameWithoutExtension.toInt()
            val arr = mapper.readTree(f) as ArrayNode
            val docs = arr.map { row ->
                ChapterRecitation(
                    key = ChapterRecitationKey(recitationId = rId, chapterId = row.get("chapter_id").asInt()),
                    audioUrl = row.get("audio_url").asText(),
                    fileSize = row.get("file_size").asLong(),
                )
            }
            if (docs.isNotEmpty()) mongo.insert(docs, ChapterRecitation::class.java)
        }
        log.info("Imported chapter recitations from {} files", files.size)
    }
}
