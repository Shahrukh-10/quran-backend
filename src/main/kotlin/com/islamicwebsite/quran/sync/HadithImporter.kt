package com.islamicwebsite.quran.sync

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.islamicwebsite.quran.domain.HadithBookEntity
import com.islamicwebsite.quran.domain.HadithEntity
import com.islamicwebsite.quran.domain.SyncState
import com.islamicwebsite.quran.repo.HadithBookRepository
import com.islamicwebsite.quran.repo.HadithRepository
import com.islamicwebsite.quran.repo.SyncStateRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong

/**
 * Loads the full Kutub as-Sittah corpus from fawazahmed0/hadith-api (jsDelivr CDN)
 * into `qh_book` / `qh_hadith` MongoDB collections on first boot. Marks itself
 * done via `qc_sync_state` and skips on subsequent restarts.
 *
 * Long-id hadith documents are assigned monotonically-increasing ids inside
 * this JVM (Mongo does not generate Long ids the way JPA IDENTITY did).
 */
@Component
class HadithImporter(
    private val mongo: MongoTemplate,
    private val booksRepo: HadithBookRepository,
    private val hadithRepo: HadithRepository,
    private val syncStateRepo: SyncStateRepository,
) {
    private val log = LoggerFactory.getLogger(HadithImporter::class.java)
    private val mapper = ObjectMapper()
    private val hadithIdSeq = AtomicLong(1)

    private data class BookSpec(
        val slug: String,
        val apiSlug: String,
        val nameEn: String,
        val nameId: String,
        val arabicName: String,
        val compilerEn: String,
        val compilerId: String,
        val compilerArabic: String,
        val eraCe: String,
        val displayOrder: Int,
        val descriptionEn: String,
        val descriptionId: String,
    )

    // Mirrors the frontend HADITH_BOOKS manifest in lib/hadith.ts.
    private val books = listOf(
        BookSpec(
            "bukhari", "bukhari", "Sahih al-Bukhari", "Sahih al-Bukhari",
            "صحيح البخاري", "Imam Muhammad al-Bukhari", "Imam Muhammad al-Bukhari",
            "محمد بن إسماعيل البخاري", "846 CE / 232 AH", 1,
            "The most authentic collection of hadith after the Quran itself. Compiled over 16 years from ~600,000 narrations.",
            "Kumpulan hadis paling sahih setelah Al-Qur'an. Disusun selama 16 tahun dari sekitar 600.000 riwayat."
        ),
        BookSpec(
            "muslim", "muslim", "Sahih Muslim", "Sahih Muslim",
            "صحيح مسلم", "Imam Muslim ibn al-Hajjaj", "Imam Muslim ibn al-Hajjaj",
            "مسلم بن الحجاج", "875 CE / 261 AH", 2,
            "The second most authentic collection of hadith. Known for its rigorous chain-of-narrator methodology.",
            "Kumpulan hadis paling sahih kedua. Dikenal karena metodologi rantai perawi yang ketat."
        ),
        BookSpec(
            "abudawud", "abudawud", "Sunan Abu Dawud", "Sunan Abu Dawud",
            "سنن أبي داود", "Imam Abu Dawud as-Sijistani", "Imam Abu Dawud as-Sijistani",
            "أبو داود السجستاني", "888 CE / 275 AH", 3,
            "Focused on legal (fiqh) hadiths. Abu Dawud selected from ~500,000 narrations.",
            "Fokus pada hadis-hadis hukum (fiqih). Abu Dawud memilih dari sekitar 500.000 riwayat."
        ),
        BookSpec(
            "tirmidhi", "tirmidhi", "Jami' at-Tirmidhi", "Jami' at-Tirmidhi",
            "جامع الترمذي", "Imam Abu Isa at-Tirmidhi", "Imam Abu Isa at-Tirmidhi",
            "أبو عيسى الترمذي", "892 CE / 279 AH", 4,
            "Notable for grading each hadith (sahih, hasan, da'if) — a first in hadith scholarship.",
            "Terkenal karena memberi derajat pada setiap hadis (sahih, hasan, dhaif) — hal baru dalam ilmu hadis."
        ),
        BookSpec(
            "nasai", "nasai", "Sunan an-Nasa'i", "Sunan an-Nasa'i",
            "سنن النسائي", "Imam an-Nasa'i", "Imam an-Nasa'i",
            "أحمد بن شعيب النسائي", "915 CE / 303 AH", 5,
            "The strictest of the four Sunan in narrator criticism. Also called al-Sunan al-Sughra.",
            "Sunan paling ketat dalam kritik perawi. Juga disebut al-Sunan al-Sughra."
        ),
        BookSpec(
            "ibnmajah", "ibnmajah", "Sunan Ibn Majah", "Sunan Ibn Majah",
            "سنن ابن ماجه", "Imam Ibn Majah", "Imam Ibn Majah",
            "محمد بن يزيد بن ماجه", "887 CE / 273 AH", 6,
            "The last of the Six Books to be widely accepted. Contains ~1,300 unique hadiths not in the others.",
            "Kitab keenam terakhir yang diterima luas. Berisi sekitar 1.300 hadis unik yang tidak ada di kitab lain."
        ),
    )

    companion object {
        private const val MARKER = "hadith:bootstrap:v1"
        private const val BATCH_SIZE = 500
        private const val CDN = "https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1/editions"
    }

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    @EventListener(ApplicationReadyEvent::class)
    fun run() {
        val marker = syncStateRepo.findById(MARKER).orElse(null)
        if (marker != null && marker.status == "OK") {
            log.info("HadithImporter: marker '{}' present — skipping (rows={})", MARKER, hadithRepo.count())
            return
        }

        val started = System.currentTimeMillis()
        log.info("HadithImporter: starting corpus load (marker '{}' missing or not OK)", MARKER)

        // Reset any partial state.
        hadithRepo.deleteAll()
        booksRepo.deleteAll()
        // Seed id sequence past anything that survived (should be zero after delete).
        hadithIdSeq.set(hadithRepo.count() + 1)

        upsertBooks()

        var totalHadiths = 0L
        for (spec in books) {
            val n = importBook(spec)
            totalHadiths += n
            log.info("HadithImporter: {} loaded {} rows", spec.slug, n)
        }

        // Update qh_book.total_hadith to match reality.
        for (spec in books) {
            val actual = hadithRepo.countByBookSlug(spec.slug).toInt()
            val book = booksRepo.findById(spec.slug).orElse(null)
            if (book != null) {
                book.totalHadith = actual
                booksRepo.save(book)
            }
        }

        // Mark done.
        val state = SyncState(
            resourceFilter = MARKER,
            status = "OK",
            lastSyncAt = Instant.now(),
            lastAttemptAt = Instant.now(),
            contentVersion = 1,
        )
        syncStateRepo.save(state)

        val secs = (System.currentTimeMillis() - started) / 1000.0
        log.info("HadithImporter: complete — {} hadiths across {} books in {}s", totalHadiths, books.size, secs)
    }

    private fun upsertBooks() {
        val docs = books.map { b ->
            HadithBookEntity(
                slug = b.slug,
                apiSlug = b.apiSlug,
                nameEn = b.nameEn,
                nameId = b.nameId,
                arabicName = b.arabicName,
                compilerEn = b.compilerEn,
                compilerId = b.compilerId,
                compilerArabic = b.compilerArabic,
                eraCe = b.eraCe,
                totalHadith = 0,
                displayOrder = b.displayOrder,
                descriptionEn = b.descriptionEn,
                descriptionId = b.descriptionId,
            )
        }
        mongo.insert(docs, HadithBookEntity::class.java)
    }

    /** Downloads eng/ara/ind editions in parallel, merges by hadithnumber, bulk-inserts. */
    private fun importBook(spec: BookSpec): Int {
        val engF = fetchAsync("$CDN/eng-${spec.apiSlug}.min.json")
        val araF = fetchAsync("$CDN/ara-${spec.apiSlug}.min.json")
        val indF = fetchAsync("$CDN/ind-${spec.apiSlug}.min.json") // may 404 → empty

        val eng = engF.join()
        val ara = araF.join()
        val ind = try { indF.join() } catch (_: Throwable) { null }

        val engIdx = indexByNumber(eng)
        val araIdx = indexByNumber(ara)
        val indIdx = ind?.let { indexByNumber(it) } ?: emptyMap()

        val sectionOf = buildSectionMap(eng)

        val ordered: List<String> = when {
            eng.size() >= ara.size() -> orderedNumbers(eng)
            else -> orderedNumbers(ara)
        }
        val seen = ordered.toMutableSet()
        val extras = mutableListOf<String>()
        for (n in araIdx.keys) if (seen.add(n)) extras += n
        for (n in indIdx.keys) if (seen.add(n)) extras += n
        val allNumbers = ordered + extras

        var ordinal = 0
        var inserted = 0
        val buf = ArrayList<HadithEntity>(BATCH_SIZE)
        for (num in allNumbers) {
            ordinal++
            val e = engIdx[num]
            val a = araIdx[num]
            val i = indIdx[num]
            val arabic = a?.get("text")?.asText() ?: ""
            val textEn = e?.get("text")?.asText() ?: ""
            val textId = i?.get("text")?.asText() ?: ""
            if (arabic.isEmpty() && textEn.isEmpty() && textId.isEmpty()) continue

            val grade = pickGrade(e ?: a ?: i)
            val section = sectionOf[num] ?: 0
            buf += HadithEntity(
                id = hadithIdSeq.getAndIncrement(),
                bookSlug = spec.slug,
                number = num,
                ordinal = ordinal,
                section = section,
                arabic = arabic,
                textEn = textEn,
                textId = textId,
                grade = grade,
            )
            if (buf.size >= BATCH_SIZE) {
                mongo.insert(buf, HadithEntity::class.java); inserted += buf.size; buf.clear()
            }
        }
        if (buf.isNotEmpty()) {
            mongo.insert(buf, HadithEntity::class.java); inserted += buf.size; buf.clear()
        }
        return inserted
    }

    private fun fetchAsync(url: String): CompletableFuture<ArrayNode> {
        val req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMinutes(3))
            .header("User-Agent", "quran-backend HadithImporter")
            .GET().build()
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofInputStream()).thenApply { resp ->
            if (resp.statusCode() != 200) {
                throw RuntimeException("GET $url returned ${resp.statusCode()}")
            }
            resp.body().use { input -> mapper.readTree(input).get("hadiths") as ArrayNode }
        }
    }

    private fun indexByNumber(arr: ArrayNode): Map<String, JsonNode> {
        val m = LinkedHashMap<String, JsonNode>(arr.size())
        for (h in arr) {
            val key = numberKey(h.get("hadithnumber")) ?: continue
            m.putIfAbsent(key, h)
        }
        return m
    }

    private fun orderedNumbers(arr: ArrayNode): List<String> {
        val seen = LinkedHashSet<String>(arr.size())
        for (h in arr) {
            val k = numberKey(h.get("hadithnumber")) ?: continue
            seen.add(k)
        }
        return seen.toList()
    }

    private fun numberKey(n: JsonNode?): String? {
        if (n == null || n.isNull) return null
        if (n.isInt || n.isLong) return n.asLong().toString()
        if (n.isFloatingPointNumber) {
            val d = n.asDouble()
            return if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
        }
        return n.asText().takeIf { it.isNotBlank() }
    }

    private fun buildSectionMap(engHadiths: ArrayNode): Map<String, Int> {
        val out = HashMap<String, Int>(engHadiths.size())
        for (h in engHadiths) {
            val key = numberKey(h.get("hadithnumber")) ?: continue
            val ref = h.get("reference")
            val sec = ref?.get("book")?.asInt(0) ?: 0
            out[key] = sec
        }
        return out
    }

    private fun pickGrade(node: JsonNode?): String? {
        val grades = node?.get("grades") as? ArrayNode ?: return null
        if (grades.isEmpty) return null
        for (g in grades) {
            val gr = g.get("grade")?.asText().orEmpty()
            if (gr.isNotBlank()) return gr.take(64)
        }
        return null
    }
}
