package com.islamicwebsite.quran.admin

import com.islamicwebsite.quran.domain.Masjid
import com.islamicwebsite.quran.repo.MasjidRepository
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

/**
 * Admin-only masjid moderation. All endpoints under `/admin/…` require the
 * ADMIN role (see SecurityConfig — HTTP Basic auth, admin/${ADMIN_PASSWORD}).
 *
 * Workflow:
 *   1. Anonymous submissions land in `/api/masjids` with status=pending.
 *   2. Admin GETs `/admin/masjids/pending` to see the queue.
 *   3. Admin PATCHes each entry with status='approved' or 'rejected'.
 *   4. Approved entries become visible on the public masjid API.
 *
 * The full record — including submitter contact — is exposed to admins here
 * (unlike the public API which strips submitter fields).
 */
@RestController
@RequestMapping("/admin/masjids")
class AdminMasjidApi(private val masjids: MasjidRepository) {

    @GetMapping("/pending")
    fun pending(): List<Masjid> =
        masjids.findByStatusOrderByCreatedAtDesc("pending")

    @GetMapping("/approved")
    fun approved(): List<Masjid> =
        masjids.findByStatusOrderByCityAsc("approved")

    @GetMapping("/rejected")
    fun rejected(): List<Masjid> =
        masjids.findByStatusOrderByCreatedAtDesc("rejected")

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ResponseEntity<Masjid> =
        masjids.findById(id).map { ResponseEntity.ok(it) }
            .orElseGet { ResponseEntity.notFound().build() }

    /**
     * Update status and/or any editable field. Empty/missing fields in the
     * payload are ignored (partial update). `status` accepted values:
     * pending, approved, rejected.
     */
    @PatchMapping("/{id}")
    @Transactional
    fun update(
        @PathVariable id: Long,
        @RequestBody patch: Map<String, Any?>
    ): ResponseEntity<Masjid> {
        val m = masjids.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()

        (patch["status"] as? String)?.let {
            if (it in setOf("pending", "approved", "rejected")) m.status = it
        }
        (patch["name"] as? String)?.let { m.name = it }
        (patch["city"] as? String)?.let { m.city = it }
        (patch["country"] as? String)?.let { m.country = it.uppercase() }
        (patch["slug"] as? String)?.let { m.slug = it }
        (patch["address"] as? String?)?.let { m.address = it }
        (patch["timezone"] as? String?)?.let { m.timezone = it }
        (patch["notes"] as? String?)?.let { m.notes = it }
        (patch["iqamahFajr"] as? String?)?.let { m.iqamahFajr = it }
        (patch["iqamahDhuhr"] as? String?)?.let { m.iqamahDhuhr = it }
        (patch["iqamahAsr"] as? String?)?.let { m.iqamahAsr = it }
        (patch["iqamahMaghrib"] as? String?)?.let { m.iqamahMaghrib = it }
        (patch["iqamahIsha"] as? String?)?.let { m.iqamahIsha = it }
        (patch["iqamahJumuah"] as? String?)?.let { m.iqamahJumuah = it }
        (patch["latitude"] as? Number?)?.let { m.latitude = it.toDouble() }
        (patch["longitude"] as? Number?)?.let { m.longitude = it.toDouble() }
        m.updatedAt = System.currentTimeMillis()
        return ResponseEntity.ok(masjids.save(m))
    }

    @DeleteMapping("/{id}")
    @Transactional
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        return if (masjids.existsById(id)) {
            masjids.deleteById(id)
            ResponseEntity.noContent().build()
        } else ResponseEntity.notFound().build()
    }
}
