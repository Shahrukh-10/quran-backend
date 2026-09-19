package com.islamicwebsite.quran.admin

import com.fasterxml.jackson.databind.ObjectMapper
import com.islamicwebsite.quran.domain.SyncState
import com.islamicwebsite.quran.repo.AyahRecitationRepository
import com.islamicwebsite.quran.repo.ChapterRepository
import com.islamicwebsite.quran.repo.RecitationRepository
import com.islamicwebsite.quran.repo.SyncStateRepository
import com.islamicwebsite.quran.repo.TafsirRepository
import com.islamicwebsite.quran.repo.TafsirVerseRepository
import com.islamicwebsite.quran.repo.TranslationRepository
import com.islamicwebsite.quran.repo.TranslationVerseRepository
import com.islamicwebsite.quran.repo.VerseRepository
import com.islamicwebsite.quran.repo.WordRepository
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import java.io.File
import java.time.Instant

@Controller
@RequestMapping("/admin")
class AdminController(
    private val syncStates: SyncStateRepository,
    private val chapters: ChapterRepository,
    private val verses: VerseRepository,
    private val words: WordRepository,
    private val translations: TranslationRepository,
    private val translationVerses: TranslationVerseRepository,
    private val tafsirs: TafsirRepository,
    private val tafsirVerses: TafsirVerseRepository,
    private val recitations: RecitationRepository,
    private val ayahRecitations: AyahRecitationRepository,
    private val mapper: ObjectMapper,
) {
    @GetMapping("", "/")
    fun dashboard(model: Model): String {
        model.addAttribute(
            "counts",
            linkedMapOf(
                "chapters" to chapters.count(),
                "verses" to verses.count(),
                "words" to words.count(),
                "translations" to translations.count(),
                "translationVerses" to translationVerses.count(),
                "tafsirs" to tafsirs.count(),
                "tafsirVerses" to tafsirVerses.count(),
                "recitations" to recitations.count(),
                "ayahRecitations" to ayahRecitations.count(),
            )
        )
        model.addAttribute("syncStates", syncStates.findAll().sortedBy { it.resourceFilter })

        val jsSyncStateFile = File("../quran-data/sync-state.json")
        model.addAttribute("jsSyncState", if (jsSyncStateFile.exists()) jsSyncStateFile.readText() else null)
        model.addAttribute("appTime", Instant.now().toString())
        return "admin/dashboard"
    }

    @PostMapping("/sync/refresh")
    fun refreshSyncStateFromFile(): String {
        val file = File("../quran-data/sync-state.json")
        if (file.exists()) {
            val root = mapper.readTree(file)
            val resources = root.get("resources")
            resources?.fields()?.forEach { (name, status) ->
                val existing = syncStates.findById(name).orElse(SyncState(resourceFilter = name))
                existing.status = status.get("status")?.asText() ?: "never"
                existing.lastSyncAt = status.get("lastSyncAt")?.asText()?.let {
                    runCatching { Instant.parse(it) }.getOrNull()
                }
                existing.contentVersion = status.get("contentVersion")?.asInt() ?: 0
                syncStates.save(existing)
            }
        }
        return "redirect:/admin"
    }
}
