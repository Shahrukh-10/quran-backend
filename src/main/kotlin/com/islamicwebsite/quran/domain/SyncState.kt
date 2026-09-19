package com.islamicwebsite.quran.domain

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "qc_sync_state")
class SyncState(
    @Id
    @Column(name = "resource_filter") var resourceFilter: String = "",
    @Column(name = "sync_token") var syncToken: String? = null,
    @Column(name = "status") var status: String = "",
    @Column(name = "last_sync_at") var lastSyncAt: Instant? = null,
    @Column(name = "last_attempt_at") var lastAttemptAt: Instant? = null,
    @Column(name = "error_message", columnDefinition = "CLOB") var errorMessage: String? = null,
    @Column(name = "content_version", nullable = false) var contentVersion: Int = 0
)
