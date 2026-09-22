package com.islamicwebsite.quran.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "qc_sync_state")
class SyncState(
    @Id var resourceFilter: String = "",
    var syncToken: String? = null,
    var status: String = "",
    var lastSyncAt: Instant? = null,
    var lastAttemptAt: Instant? = null,
    var errorMessage: String? = null,
    var contentVersion: Int = 0
)
