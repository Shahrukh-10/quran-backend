package com.islamicwebsite.quran.api

import com.islamicwebsite.quran.domain.AppUser
import com.islamicwebsite.quran.domain.UserSession
import com.islamicwebsite.quran.domain.UserState
import com.islamicwebsite.quran.repo.AppUserRepository
import com.islamicwebsite.quran.repo.SequenceGenerator
import com.islamicwebsite.quran.repo.UserSessionRepository
import com.islamicwebsite.quran.repo.UserStateRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.web.bind.annotation.*
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Optional user accounts (V3).
 *
 * Passphrase-only auth — no email, no OAuth, no recovery. Users pick a username
 * and a passphrase; they can log in on any device to sync bookmarks/notes/etc.
 * Losing the passphrase means losing the account (documented on the frontend).
 *
 * Endpoints (all under /api/auth):
 *   POST /api/auth/register        — create account, returns session token
 *   POST /api/auth/login           — authenticate, returns session token
 *   POST /api/auth/logout          — revoke current session
 *   GET  /api/auth/me              — return current user (auth required)
 *   GET  /api/auth/sync            — download user state blob
 *   PUT  /api/auth/sync            — upload user state blob with etag concurrency check
 */
@RestController
@RequestMapping("/api/auth")
class AuthApi(
    private val users: AppUserRepository,
    private val sessions: UserSessionRepository,
    private val states: UserStateRepository,
    private val pw: BCryptPasswordEncoder,
    private val seq: SequenceGenerator,
) {

    // ---------- register ----------

    @PostMapping("/register")
    fun register(@RequestBody body: RegisterRequest): ResponseEntity<Any> {
        val username = body.username?.trim()?.lowercase() ?: return ResponseEntity.badRequest().body(mapOf("error" to "username required"))
        val passphrase = body.passphrase ?: return ResponseEntity.badRequest().body(mapOf("error" to "passphrase required"))
        if (!isValidUsername(username)) return ResponseEntity.badRequest().body(mapOf("error" to "username must be 3-64 chars, a-z 0-9 _ -"))
        if (passphrase.length < 12) return ResponseEntity.badRequest().body(mapOf("error" to "passphrase must be at least 12 characters"))
        if (users.existsByUsername(username)) return ResponseEntity.status(409).body(mapOf("error" to "username taken"))

        val now = System.currentTimeMillis()
        val newId = seq.nextValue("app_user")
        val user = users.save(AppUser(id = newId, username = username, passwordHash = pw.encode(passphrase), createdAt = now))
        val token = newToken()
        sessions.save(UserSession(token = token, userId = user.id!!, createdAt = now, expiresAt = now + SESSION_TTL_MS))
        return ResponseEntity.ok(SessionResponse(token = token, username = user.username, userId = user.id!!))
    }

    // ---------- login ----------

    @PostMapping("/login")
    fun login(@RequestBody body: LoginRequest): ResponseEntity<Any> {
        val username = body.username?.trim()?.lowercase() ?: return ResponseEntity.badRequest().body(mapOf("error" to "username required"))
        val passphrase = body.passphrase ?: return ResponseEntity.badRequest().body(mapOf("error" to "passphrase required"))
        val user = users.findByUsername(username) ?: return ResponseEntity.status(401).body(mapOf("error" to "invalid credentials"))
        if (!pw.matches(passphrase, user.passwordHash)) return ResponseEntity.status(401).body(mapOf("error" to "invalid credentials"))
        val now = System.currentTimeMillis()
        val token = newToken()
        sessions.save(UserSession(token = token, userId = user.id!!, createdAt = now, expiresAt = now + SESSION_TTL_MS))
        return ResponseEntity.ok(SessionResponse(token = token, username = user.username, userId = user.id!!))
    }

    // ---------- logout ----------

    @PostMapping("/logout")
    fun logout(@RequestHeader("Authorization", required = false) auth: String?): ResponseEntity<Any> {
        val token = extractToken(auth) ?: return ResponseEntity.ok(mapOf("ok" to true))
        sessions.deleteByToken(token)
        return ResponseEntity.ok(mapOf("ok" to true))
    }

    // ---------- me ----------

    @GetMapping("/me")
    fun me(@RequestHeader("Authorization", required = false) auth: String?): ResponseEntity<Any> {
        val user = authenticate(auth) ?: return unauthorized()
        return ResponseEntity.ok(mapOf("userId" to user.id, "username" to user.username, "createdAt" to user.createdAt))
    }

    // ---------- sync GET ----------

    @GetMapping("/sync")
    fun getState(@RequestHeader("Authorization", required = false) auth: String?): ResponseEntity<Any> {
        val user = authenticate(auth) ?: return unauthorized()
        val state = states.findById(user.id!!).orElse(null)
        return if (state == null) {
            ResponseEntity.ok(SyncPayload(state = "{}", etag = "empty", updatedAt = 0))
        } else {
            ResponseEntity.ok(SyncPayload(state = state.stateJson, etag = state.etag, updatedAt = state.updatedAt))
        }
    }

    // ---------- sync PUT ----------

    @PutMapping("/sync")
    fun putState(
        @RequestHeader("Authorization", required = false) auth: String?,
        @RequestHeader("If-Match", required = false) ifMatch: String?,
        @RequestBody body: SyncPutRequest,
    ): ResponseEntity<Any> {
        val user = authenticate(auth) ?: return unauthorized()
        val payload = body.state ?: return ResponseEntity.badRequest().body(mapOf("error" to "state required"))
        // Guard against runaway blobs — user data should be well under 1 MB.
        if (payload.length > 1_048_576) return ResponseEntity.status(413).body(mapOf("error" to "state too large (max 1 MiB)"))

        val existing = states.findById(user.id!!).orElse(null)
        // If client sent If-Match, honor optimistic concurrency. First-write clients omit it.
        if (existing != null && ifMatch != null && ifMatch != existing.etag) {
            return ResponseEntity.status(412).body(mapOf("error" to "etag mismatch", "currentEtag" to existing.etag))
        }
        val now = System.currentTimeMillis()
        val newEtag = etagOf(payload, now)
        val row = existing ?: UserState(userId = user.id!!)
        row.stateJson = payload
        row.etag = newEtag
        row.updatedAt = now
        states.save(row)
        return ResponseEntity.ok(SyncPayload(state = payload, etag = newEtag, updatedAt = now))
    }

    // ---------- helpers ----------

    private fun authenticate(authHeader: String?): AppUser? {
        val token = extractToken(authHeader) ?: return null
        val session = sessions.findByToken(token) ?: return null
        if (session.expiresAt < System.currentTimeMillis()) {
            sessions.deleteByToken(token)
            return null
        }
        return users.findById(session.userId).orElse(null)
    }

    private fun extractToken(authHeader: String?): String? {
        if (authHeader == null) return null
        if (!authHeader.startsWith("Bearer ", ignoreCase = true)) return null
        return authHeader.substring(7).trim().takeIf { it.isNotEmpty() }
    }

    private fun unauthorized(): ResponseEntity<Any> =
        ResponseEntity.status(401).body(mapOf("error" to "authentication required"))

    private fun newToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun etagOf(payload: String, ts: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest((payload + ts).toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }.substring(0, 32)
    }

    private fun isValidUsername(name: String): Boolean =
        name.length in 3..64 && name.all { it.isLetterOrDigit() || it == '_' || it == '-' }

    companion object {
        private const val SESSION_TTL_MS = 90L * 24 * 60 * 60 * 1000L // 90 days
    }
}

data class RegisterRequest(val username: String? = null, val passphrase: String? = null)
data class LoginRequest(val username: String? = null, val passphrase: String? = null)
data class SessionResponse(val token: String, val username: String, val userId: Long)
data class SyncPayload(val state: String, val etag: String, val updatedAt: Long)
data class SyncPutRequest(val state: String? = null)
