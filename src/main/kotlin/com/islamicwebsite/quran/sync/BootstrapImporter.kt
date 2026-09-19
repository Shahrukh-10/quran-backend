package com.islamicwebsite.quran.sync

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.islamicwebsite.quran.repo.ChapterRepository
import com.islamicwebsite.quran.repo.RecitationRepository
import com.islamicwebsite.quran.repo.TafsirRepository
import com.islamicwebsite.quran.repo.TranslationRepository
import com.islamicwebsite.quran.repo.VerseRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.io.File

@Component
class BootstrapImporter(
    private val jdbc: JdbcTemplate,
    private val chapterRepo: ChapterRepository,
    private val verseRepo: VerseRepository,
    private val translationRepo: TranslationRepository,
    private val tafsirRepo: TafsirRepository,
    private val recitationRepo: RecitationRepository,
    @Value("\${quran.data.root:../quran-data}") private val dataRoot: String,
) {
    private val log = LoggerFactory.getLogger(BootstrapImporter::class.java)
    private val mapper = ObjectMapper()

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
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
        val arr = mapper.readTree(file) as ArrayNode
        val sql = "INSERT INTO qc_chapter (id, revelation_place, revelation_order, bismillah_pre, name_simple, name_complex, name_arabic, verses_count, page_start, page_end, translated_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        val batch = arr.map { row ->
            val pages = row.get("pages") as ArrayNode
            arrayOf<Any?>(
                row.get("id").asInt(),
                row.get("revelation_place").asText(),
                row.get("revelation_order").asInt(),
                row.get("bismillah_pre").asBoolean(),
                row.get("name_simple").asText(),
                row.get("name_complex").asText(),
                row.get("name_arabic").asText(),
                row.get("verses_count").asInt(),
                pages.get(0).asInt(),
                pages.get(1).asInt(),
                row.get("translated_name")?.get("name")?.asText() ?: "",
            )
        }
        jdbc.batchUpdate(sql, batch)
        log.info("Imported {} chapters", batch.size)
    }

    private fun importVerses(dir: File) {
        val verseSql = "INSERT INTO qc_verse (verse_key, chapter_id, verse_number, juz_number, hizb_number, rub_el_hizb_number, ruku_number, manzil_number, page_number, sajdah_number, text_uthmani, text_indopak, text_imlaei) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        val wordSql = "INSERT INTO qc_word (verse_key, position, char_type_name, text_uthmani, text_indopak, translation, transliteration, audio_relative_url, page_number, line_number) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        var verseCount = 0L
        var wordCount = 0L
        val verseBatch = mutableListOf<Array<Any?>>()
        val wordBatch = mutableListOf<Array<Any?>>()
        for (s in 1..114) {
            val file = File(dir, "$s.json")
            if (!file.exists()) continue
            val arr = mapper.readTree(file) as ArrayNode
            for (v in arr) {
                verseBatch += arrayOf(
                    v.get("verse_key").asText(),
                    s,
                    v.get("verse_number").asInt(),
                    v.get("juz_number").asInt(),
                    v.get("hizb_number").asInt(),
                    v.get("rub_el_hizb_number")?.takeUnless { it.isNull }?.asInt(),
                    v.get("ruku_number").asInt(),
                    v.get("manzil_number").asInt(),
                    v.get("page_number").asInt(),
                    v.get("sajdah_number")?.takeUnless { it.isNull }?.asInt(),
                    v.get("text_uthmani").asText(),
                    v.get("text_indopak")?.takeUnless { it.isNull }?.asText(),
                    v.get("text_imlaei")?.takeUnless { it.isNull }?.asText(),
                )
                val words = v.get("words") as? ArrayNode
                if (words != null) {
                    for (w in words) {
                        wordBatch += arrayOf(
                            v.get("verse_key").asText(),
                            w.get("position").asInt(),
                            w.get("char_type_name").asText(),
                            w.get("text_uthmani").asText(),
                            w.get("text_indopak")?.takeUnless { it.isNull }?.asText(),
                            w.get("translation")?.get("text")?.asText(),
                            w.get("transliteration")?.get("text")?.asText(),
                            w.get("audio_url")?.takeUnless { it.isNull }?.asText(),
                            w.get("page_number").asInt(),
                            w.get("line_number").asInt(),
                        )
                    }
                }
                // Word rows FK to qc_verse.verse_key, so we must flush verses before words.
                if (wordBatch.size >= 1000) {
                    if (verseBatch.isNotEmpty()) {
                        jdbc.batchUpdate(verseSql, verseBatch); verseCount += verseBatch.size; verseBatch.clear()
                    }
                    jdbc.batchUpdate(wordSql, wordBatch); wordCount += wordBatch.size; wordBatch.clear()
                } else if (verseBatch.size >= 500) {
                    jdbc.batchUpdate(verseSql, verseBatch); verseCount += verseBatch.size; verseBatch.clear()
                }
            }
        }
        if (verseBatch.isNotEmpty()) {
            jdbc.batchUpdate(verseSql, verseBatch); verseCount += verseBatch.size; verseBatch.clear()
        }
        if (wordBatch.isNotEmpty()) {
            jdbc.batchUpdate(wordSql, wordBatch); wordCount += wordBatch.size; wordBatch.clear()
        }
        log.info("Imported {} verses, {} words", verseCount, wordCount)
    }

    private fun importRecitations(file: File) {
        if (!file.exists()) return
        val arr = mapper.readTree(file) as ArrayNode
        val sql = "INSERT INTO qc_recitation (id, reciter_name, style) VALUES (?, ?, ?)"
        val batch = arr.map { row ->
            arrayOf<Any?>(
                row.get("id").asInt(),
                row.get("reciter_name").asText(),
                row.get("style")?.takeUnless { it.isNull }?.asText(),
            )
        }
        jdbc.batchUpdate(sql, batch)
        log.info("Imported {} recitations", batch.size)
    }

    private fun importTranslations(catalogFile: File, dir: File) {
        val catalog = mapper.readTree(catalogFile) as ArrayNode
        val syncedIds = dir.listFiles { f -> f.isDirectory && f.name.matches(Regex("\\d+")) }
            ?.map { it.name.toInt() }?.toSet() ?: emptySet()
        val catSql = "INSERT INTO qc_translation (id, name, author_name, slug, language_name) VALUES (?, ?, ?, ?, ?)"
        for (t in catalog) {
            val id = t.get("id").asInt()
            if (id !in syncedIds) continue
            jdbc.update(
                catSql, id, t.get("name").asText(), t.get("author_name").asText(),
                t.get("slug").asText(), t.get("language_name").asText()
            )
        }

        val rowSql = "INSERT INTO qc_translation_verse (translation_id, verse_key, text) VALUES (?, ?, ?)"
        for (id in syncedIds) {
            val batch = mutableListOf<Array<Any?>>()
            var total = 0L
            for (s in 1..114) {
                val f = File(dir, "$id/$s.json"); if (!f.exists()) continue
                val rows = mapper.readTree(f) as ArrayNode
                for (r in rows) batch += arrayOf(id, r.get("verse_key").asText(), r.get("text").asText())
                if (batch.size >= 2000) {
                    jdbc.batchUpdate(rowSql, batch); total += batch.size; batch.clear()
                }
            }
            if (batch.isNotEmpty()) {
                jdbc.batchUpdate(rowSql, batch); total += batch.size
            }
            log.info("Imported translation {} ({} rows)", id, total)
        }
    }

    private fun importTafsirs(catalogFile: File, dir: File) {
        val catalog = mapper.readTree(catalogFile) as ArrayNode
        val syncedIds = dir.listFiles { f -> f.isDirectory && f.name.matches(Regex("\\d+")) }
            ?.map { it.name.toInt() }?.toSet() ?: emptySet()
        val catSql = "INSERT INTO qc_tafsir (id, name, author_name, slug, language_name) VALUES (?, ?, ?, ?, ?)"
        for (t in catalog) {
            val id = t.get("id").asInt()
            if (id !in syncedIds) continue
            jdbc.update(
                catSql, id, t.get("name").asText(), t.get("author_name").asText(),
                t.get("slug").asText(), t.get("language_name").asText()
            )
        }

        val rowSql = "INSERT INTO qc_tafsir_verse (tafsir_id, verse_key, text) VALUES (?, ?, ?)"
        for (id in syncedIds) {
            val batch = mutableListOf<Array<Any?>>()
            var total = 0L
            for (s in 1..114) {
                val f = File(dir, "$id/$s.json"); if (!f.exists()) continue
                val rows = mapper.readTree(f) as ArrayNode
                for (r in rows) batch += arrayOf(id, r.get("verse_key").asText(), r.get("text").asText())
                if (batch.size >= 500) {
                    jdbc.batchUpdate(rowSql, batch); total += batch.size; batch.clear()
                }
            }
            if (batch.isNotEmpty()) {
                jdbc.batchUpdate(rowSql, batch); total += batch.size
            }
            log.info("Imported tafsir {} ({} rows)", id, total)
        }
    }

    private fun importAudioTimings(dir: File) {
        val sql = "INSERT INTO qc_ayah_recitation (recitation_id, verse_key, audio_url, segments) VALUES (?, ?, ?, ?)"
        val reciters = dir.listFiles { f -> f.isDirectory && f.name.matches(Regex("\\d+")) } ?: return
        for (rDir in reciters) {
            val rId = rDir.name.toInt()
            val batch = mutableListOf<Array<Any?>>()
            var total = 0L
            for (s in 1..114) {
                val f = File(rDir, "$s.json"); if (!f.exists()) continue
                val rows = mapper.readTree(f) as ArrayNode
                for (row in rows) {
                    val url = row.get("audio_url")?.takeUnless { it.isNull }?.asText() ?: continue
                    batch += arrayOf(
                        rId,
                        row.get("verse_key").asText(),
                        url,
                        mapper.writeValueAsString(row.get("segments")),
                    )
                }
                if (batch.size >= 2000) {
                    jdbc.batchUpdate(sql, batch); total += batch.size; batch.clear()
                }
            }
            if (batch.isNotEmpty()) {
                jdbc.batchUpdate(sql, batch); total += batch.size
            }
            log.info("Imported audio timings for reciter {} ({} rows)", rId, total)
        }
    }

    private fun importChapterRecitations(dir: File) {
        val sql = "INSERT INTO qc_chapter_recitation (recitation_id, chapter_id, audio_url, file_size) VALUES (?, ?, ?, ?)"
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return
        for (f in files) {
            val rId = f.nameWithoutExtension.toInt()
            val arr = mapper.readTree(f) as ArrayNode
            val batch = arr.map { row ->
                arrayOf<Any?>(
                    rId,
                    row.get("chapter_id").asInt(),
                    row.get("audio_url").asText(),
                    row.get("file_size").asLong(),
                )
            }
            jdbc.batchUpdate(sql, batch)
        }
        log.info("Imported chapter recitations from {} files", files.size)
    }
}
