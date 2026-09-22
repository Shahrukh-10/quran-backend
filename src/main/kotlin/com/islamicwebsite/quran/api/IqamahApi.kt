package com.islamicwebsite.quran.api

import com.islamicwebsite.quran.domain.Masjid
import com.islamicwebsite.quran.repo.MasjidRepository
import com.islamicwebsite.quran.repo.SequenceGenerator
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

/**
 * Iqamah (masjid schedule) endpoints.
 *
 * Public reads return only APPROVED masjids so pending submissions never leak.
 * Anonymous submissions land in the `pending` bucket and become visible only
 * after an admin approves them via /admin/masjids (see AdminController).
 *
 * Endpoints:
 *   GET  /api/masjids                → list all approved (city-sorted)
 *   GET  /api/masjids/{slug}          → single masjid detail
 *   GET  /api/masjids/by-country/{c}  → filter (country stored ISO-lite, "GB", "US", ...)
 *   POST /api/masjids                → anonymous submission (returns pending record)
 */
@RestController
@RequestMapping("/api/masjids")
class IqamahApi(
    private val masjids: MasjidRepository,
    private val seq: SequenceGenerator,
) {

    @GetMapping
    fun list(): List<MasjidView> =
        masjids.findByStatusOrderByCityAsc("approved").map(::toView)

    @GetMapping("/{slug}")
    fun get(@PathVariable slug: String): ResponseEntity<MasjidView> {
        val m = masjids.findBySlug(slug)
        return if (m != null && m.status == "approved") ResponseEntity.ok(toView(m))
        else ResponseEntity.notFound().build()
    }

    @GetMapping("/by-country/{country}")
    fun byCountry(@PathVariable country: String): List<MasjidView> =
        masjids
            .findByCountryAndStatus(country.uppercase(), "approved")
            .map(::toView)

    /**
     * Submit a new masjid. Anonymous — no auth. Lands in `pending` for admin
     * moderation. We accept a subset of fields; server sets timestamps and
     * always forces status=pending regardless of what the client sends.
     */
    @PostMapping
    @Transactional
    fun submit(@RequestBody req: SubmitRequest): ResponseEntity<Map<String, Any?>> {
        // Validate minimum required fields.
        val name = req.name?.trim().orEmpty()
        val city = req.city?.trim().orEmpty()
        val country = req.country?.trim()?.uppercase().orEmpty()
        if (name.isBlank() || city.isBlank() || country.isBlank()) {
            return ResponseEntity.badRequest().body(
                mapOf("error" to "name, city, country are required")
            )
        }
        // Simple length caps to keep the DB predictable.
        if (name.length > 256 || city.length > 128 || country.length > 8) {
            return ResponseEntity.badRequest().body(mapOf("error" to "field too long"))
        }

        val slug = req.slug?.trim()?.lowercase()?.replace(Regex("[^a-z0-9-]+"), "-")
            ?.trim('-') ?: slugify(name, city)
        if (slug.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "could not derive slug"))
        }
        if (masjids.existsBySlug(slug)) {
            return ResponseEntity.status(409).body(mapOf("error" to "slug already exists"))
        }

        val now = System.currentTimeMillis()
        val saved = masjids.save(Masjid(
            id = seq.nextValue("masjid"),
            slug = slug,
            name = name,
            city = city,
            country = country,
            address = req.address?.trim(),
            latitude = req.latitude,
            longitude = req.longitude,
            timezone = req.timezone?.trim(),
            iqamahFajr = req.iqamahFajr?.trim(),
            iqamahDhuhr = req.iqamahDhuhr?.trim(),
            iqamahAsr = req.iqamahAsr?.trim(),
            iqamahMaghrib = req.iqamahMaghrib?.trim(),
            iqamahIsha = req.iqamahIsha?.trim(),
            iqamahJumuah = req.iqamahJumuah?.trim(),
            notes = req.notes?.trim(),
            submitterName = req.submitterName?.trim(),
            submitterEmail = req.submitterEmail?.trim(),
            status = "pending",
            createdAt = now,
            updatedAt = now,
        ))
        // Response echoes only the safe fields — no submitter email leak.
        return ResponseEntity.status(201).body(
            mapOf(
                "slug" to saved.slug,
                "status" to saved.status,
                "message" to "Submitted for moderation. Thank you.",
            )
        )
    }

    private fun slugify(name: String, city: String): String {
        val base = "$city-$name".lowercase()
            .replace(Regex("[^a-z0-9-]+"), "-")
            .trim('-')
        return base.take(96)
    }
}

/** Public-safe projection — never leaks submitter contact info. */
data class MasjidView(
    val slug: String,
    val name: String,
    val city: String,
    val country: String,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val timezone: String?,
    val iqamah: Map<String, String?>,
    val notes: String?,
    val updatedAt: Long,
)

private fun toView(m: Masjid): MasjidView = MasjidView(
    slug = m.slug,
    name = m.name,
    city = m.city,
    country = m.country,
    address = m.address,
    latitude = m.latitude,
    longitude = m.longitude,
    timezone = m.timezone,
    iqamah = mapOf(
        "fajr" to m.iqamahFajr,
        "dhuhr" to m.iqamahDhuhr,
        "asr" to m.iqamahAsr,
        "maghrib" to m.iqamahMaghrib,
        "isha" to m.iqamahIsha,
        "jumuah" to m.iqamahJumuah,
    ),
    notes = m.notes,
    updatedAt = m.updatedAt,
)

/** Anonymous submission payload. All fields optional except name+city+country. */
data class SubmitRequest(
    val slug: String? = null,
    val name: String? = null,
    val city: String? = null,
    val country: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timezone: String? = null,
    val iqamahFajr: String? = null,
    val iqamahDhuhr: String? = null,
    val iqamahAsr: String? = null,
    val iqamahMaghrib: String? = null,
    val iqamahIsha: String? = null,
    val iqamahJumuah: String? = null,
    val notes: String? = null,
    val submitterName: String? = null,
    val submitterEmail: String? = null,
)
