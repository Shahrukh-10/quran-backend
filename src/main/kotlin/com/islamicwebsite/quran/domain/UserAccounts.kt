package com.islamicwebsite.quran.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

/**
 * Optional user account. Passphrase-only (no email, no OAuth).
 * Users pick a username plus a passphrase — that pair is all we store to identify them.
 * `passwordHash` is a bcrypt-encoded passphrase.
 *
 * Mongo does NOT auto-generate Long ids like JPA IDENTITY did. The AuthApi
 * layer assigns a monotonically-increasing Long id at insert time (see
 * AuthApi.newUserId()). We keep `id: Long` to preserve the public API shape
 * (SessionResponse.userId, UserState.userId FK, /api/auth/me response).
 */
@Document(collection = "app_user")
class AppUser(
    @Id var id: Long? = null,
    @Indexed(unique = true) var username: String = "",
    var passwordHash: String = "",
    var createdAt: Long = 0,
)

/**
 * Opaque session token. When a user logs in we generate a random 32-byte hex
 * string, insert a doc here, and hand it back. Every request that wants to
 * modify user state must include this token in Authorization: Bearer.
 * Tokens expire — clients refresh by logging in again.
 */
@Document(collection = "user_session")
class UserSession(
    @Id var token: String = "",
    var userId: Long = 0,
    var createdAt: Long = 0,
    var expiresAt: Long = 0,
)

/**
 * Per-user JSON state blob — matches the shape of iw.v1 localStorage on the frontend.
 * One doc per user. `etag` is used for optimistic concurrency control on PUT sync.
 */
@Document(collection = "user_state")
class UserState(
    @Id var userId: Long = 0,
    var stateJson: String = "{}",
    var etag: String = "",
    var updatedAt: Long = 0,
)
