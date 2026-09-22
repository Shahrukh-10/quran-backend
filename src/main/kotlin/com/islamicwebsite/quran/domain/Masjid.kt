package com.islamicwebsite.quran.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

/**
 * A masjid (mosque) with its crowdsourced iqamah schedule.
 *
 * `slug` is the URL-safe identifier we hand out publicly (e.g. `masjid-al-nabawi`).
 * `status` gates visibility — only 'approved' entries are exposed via the public API.
 * Times are HH:MM strings — no timezone math, just wall-clock at the masjid.
 */
@Document(collection = "masjid")
class Masjid(
    @Id var id: Long = 0,
    var slug: String = "",
    var name: String = "",
    var city: String = "",
    var country: String = "",
    var address: String? = null,
    var latitude: Double? = null,
    var longitude: Double? = null,
    var timezone: String? = null,
    var iqamahFajr: String? = null,
    var iqamahDhuhr: String? = null,
    var iqamahAsr: String? = null,
    var iqamahMaghrib: String? = null,
    var iqamahIsha: String? = null,
    var iqamahJumuah: String? = null,
    var notes: String? = null,
    var submitterName: String? = null,
    var submitterEmail: String? = null,
    var status: String = "pending",
    var createdAt: Long = 0,
    var updatedAt: Long = 0,
)
