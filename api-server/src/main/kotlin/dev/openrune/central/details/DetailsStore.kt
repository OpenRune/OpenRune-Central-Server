package dev.openrune.central.details

import at.favre.lib.crypto.bcrypt.BCrypt
import dev.openrune.central.AppState
import dev.openrune.central.PlayerLoadResponse
import dev.openrune.central.PlayerUID
import dev.openrune.central.packet.model.LoginDetailsDto
import dev.openrune.central.packet.model.LoginResponseOutgoing
import dev.openrune.central.packet.model.PlayerDetailsDto
import dev.openrune.central.storage.JsonBucket
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object DetailsStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun normalizeName(raw: String): String = raw.trim().lowercase()

    /**
     * Deterministic 64-bit id derived from a stable string (FNV-1a 64).
     * Used to generate a UID once (on account creation/backfill).
     */
    private fun stableUidFromLoginUsername(loginUsernameLower: String): Long {
        val bytes = loginUsernameLower.toByteArray(Charsets.UTF_8)
        var hash = 1469598103934665603L // FNV offset basis (64-bit)
        val prime = 1099511628211L // FNV prime (64-bit)
        for (b in bytes) {
            hash = hash xor (b.toLong() and 0xffL)
            hash *= prime
        }
        // Avoid 0 as a sentinel/default
        return if (hash == 0L) 1L else hash
    }

    /**
     * Computes the stable uid for a login username without doing any storage or password work.
     *
     * This is safe to call early in request handling (e.g., to reject ALREADY_ONLINE).
     */
    fun stableUidForLoginUsername(usernameRaw: String): Long {
        val loginUsernameLower = normalizeName(usernameRaw)
        if (loginUsernameLower.isEmpty()) return 0L
        return stableUidFromLoginUsername(loginUsernameLower)
    }

    @Serializable
    private data class StoredAccountGroupRecord(
        @SerialName("loginUsername")
        val loginUsername: String,
        /**
         * Stable UID for this login group (set once on creation).
         * Defaults to 0 for backward compatibility with older stored records.
         */
        @SerialName("uid")
        val uid: Long = 0L,
        @SerialName("createdAt")
        val createdAt: Long,
        @SerialName("lastLogin")
        val lastLogin: Long,
        @SerialName("passwordHash")
        val passwordHash: String,
        /**
         * Keyed by account username (lowercased).
         */
        @SerialName("accounts")
        val accounts: Map<String, StoredAccountRecord> = emptyMap()
    )

    @Serializable
    private data class StoredAccountRecord(
        @SerialName("previousXteas")
        val previousXteas: List<Int> = emptyList(),
        @SerialName("previousDisplayName")
        val previousDisplayName: String = "",
        @SerialName("dateChanged")
        val dateChanged: Long = -1,
        @SerialName("registryDate")
        val registryDate: Long
    )

    @Serializable
    private data class AccountIndexRecord(
        @SerialName("account")
        val account: String,
        @SerialName("loginUsername")
        val loginUsername: String
    )

    @Serializable
    private data class UidIndexRecord(
        @SerialName("uid")
        val uid: Long,
        @SerialName("loginUsername")
        val loginUsername: String
    )



    suspend fun loadOrCreate(
        usernameRaw: String,
        password: String,
        xteas: List<Int>
    ): LoginResponseOutgoing {
        val nameLower = normalizeName(usernameRaw)
        if (nameLower.isEmpty()) return LoginResponseOutgoing(PlayerLoadResponse.MALFORMED)

        // Login is ONLY allowed via the primary login username (the group key).
        // However, when creating a new login, we must ensure the name doesn't already exist as
        // either a login name or a linked sub-account for someone else.
        val existingOwner = resolveOwnerLoginUsername(nameLower)
        if (existingOwner != null && existingOwner != nameLower) {
            // Username exists as a sub-account under another login.
            return LoginResponseOutgoing(PlayerLoadResponse.INVALID_CREDENTIALS)
        }

        val groupJson = AppState.storage.get(JsonBucket.LOGIN_DETAILS, nameLower)
        if (groupJson == null) {
            if (password.isEmpty()) return LoginResponseOutgoing(PlayerLoadResponse.MALFORMED)
            val created = createGroup(
                loginUsernameLower = nameLower,
                password = password,
                firstAccountLower = nameLower,
                xteas = xteas
            )
            val now = System.currentTimeMillis()
            val updated = created.copy(lastLogin = now)
            AppState.storage.upsert(JsonBucket.LOGIN_DETAILS, nameLower, json.encodeToString(StoredAccountGroupRecord.serializer(), updated))
            val uid = if (updated.uid == 0L) stableUidFromLoginUsername(nameLower) else updated.uid
            return LoginResponseOutgoing(
                PlayerLoadResponse.NEW_ACCOUNT,
                login = LoginDetailsDto(
                    loginUsername = nameLower,
                    createdAt = created.createdAt,
                    lastLogin = now,
                    linkedAccounts = updated.accounts.toPlayerDetailsList(PlayerUID(uid))
                )
            )
        }

        val group = loadGroup(nameLower) ?: return LoginResponseOutgoing(PlayerLoadResponse.MALFORMED)
        val account = group.accounts[nameLower]

        return try {
            // Decide auth mode: password if provided, else reconnection via xteas
            val authResult =
                if (password.isNotEmpty()) {
                    val ok = BCrypt.verifyer().verify(password.toCharArray(), group.passwordHash).verified
                    if (!ok) PlayerLoadResponse.INVALID_CREDENTIALS else PlayerLoadResponse.LOAD
                } else {
                    val prev = account?.previousXteas ?: emptyList()
                    if (prev != xteas) PlayerLoadResponse.INVALID_RECONNECTION else PlayerLoadResponse.LOAD
                }

            if (authResult != PlayerLoadResponse.LOAD) return LoginResponseOutgoing(authResult)

            // Update previousXteas on the account being logged in as
            val updatedAccounts =
                if (account == null) group.accounts
                else group.accounts + (nameLower to account.copy(previousXteas = xteas))

            val updated = group.copy(accounts = updatedAccounts, lastLogin = System.currentTimeMillis())
            AppState.storage.upsert(JsonBucket.LOGIN_DETAILS, nameLower, json.encodeToString(StoredAccountGroupRecord.serializer(), updated))
            val uid = if (updated.uid == 0L) stableUidFromLoginUsername(nameLower) else updated.uid

            LoginResponseOutgoing(
                PlayerLoadResponse.LOAD,
                login = LoginDetailsDto(
                    loginUsername = nameLower,
                    createdAt = updated.createdAt,
                    lastLogin = updated.lastLogin,
                    linkedAccounts = updated.accounts.toPlayerDetailsList(PlayerUID(uid))
                )
            )
        } catch (_: Throwable) {
            LoginResponseOutgoing(PlayerLoadResponse.MALFORMED)
        }
    }

    suspend fun linkAccount(usernameRaw: String, password: String, newAccountRaw: String, xteas: List<Int> = emptyList()): Pair<Boolean, List<String>> {
        val nameLower = normalizeName(usernameRaw)
        val newLower = normalizeName(newAccountRaw)
        if (nameLower.isEmpty() || newLower.isEmpty()) return false to emptyList()

        // Linking is only allowed when authenticating as the primary login username.
        val group = loadGroup(nameLower) ?: return false to emptyList()

        val ok = BCrypt.verifyer().verify(password.toCharArray(), group.passwordHash).verified
        if (!ok) return false to group.accounts.keys.sorted()

        // Prevent collisions: cannot link an account name that already exists anywhere.
        val existingOwnerForNew = resolveOwnerLoginUsername(newLower)
        if (existingOwnerForNew != null) return false to group.accounts.keys.sorted()

        val newDetails =
            StoredAccountRecord(
                previousXteas = xteas,
                previousDisplayName = "",
                dateChanged = -1,
                registryDate = System.currentTimeMillis()
            )

        val updated = group.copy(accounts = group.accounts + (newLower to newDetails))
        AppState.storage.upsert(JsonBucket.LOGIN_DETAILS, nameLower, json.encodeToString(StoredAccountGroupRecord.serializer(), updated))
        upsertAccountIndex(newLower, nameLower)

        return true to updated.accounts.keys.sorted()
    }

    /**
     * Persist logout state for an account (currently: previousXteas).
     *
     * Caller provides:
     * - uid: stable group uid (must match the resolved owner's uid)
     * - accountRaw: account username (may be linked sub-account)
     */
    suspend fun saveLogout(uid: Long, accountRaw: String, previousXteas: List<Int>): Boolean {
        val accountLower = normalizeName(accountRaw)
        if (uid == 0L) return false

        val owner =
            (if (accountLower.isNotEmpty()) resolveOwnerLoginUsername(accountLower)
            else resolveOwnerLoginUsernameByUid(uid))
                ?: return false
        val group = loadGroup(owner) ?: return false

        // Ensure uid matches the resolved owner group.
        val expectedUid = if (group.uid == 0L) stableUidFromLoginUsername(owner) else group.uid
        if (expectedUid != uid) return false

        val targetAccount = if (accountLower.isNotEmpty()) accountLower else owner

        // Only save if the account exists in the group.
        val existing = group.accounts[targetAccount] ?: return false

        val updatedAccounts = group.accounts + (targetAccount to existing.copy(previousXteas = previousXteas))
        val updated = group.copy(accounts = updatedAccounts)
        AppState.storage.upsert(JsonBucket.LOGIN_DETAILS, owner, json.encodeToString(StoredAccountGroupRecord.serializer(), updated))
        return true
    }

    /**
     * Validates that an account belongs to the provided uid group and returns a stable storage key.
     *
     * Key format: "{uid}:{accountLower}"
     */
    suspend fun playerSaveKey(uid: Long, accountRaw: String): String? {
        val accountLower = normalizeName(accountRaw)
        if (uid == 0L) return null

        val owner =
            (if (accountLower.isNotEmpty()) resolveOwnerLoginUsername(accountLower)
            else resolveOwnerLoginUsernameByUid(uid))
                ?: return null
        val group = loadGroup(owner) ?: return null

        val expectedUid = if (group.uid == 0L) stableUidFromLoginUsername(owner) else group.uid
        if (expectedUid != uid) return null

        val targetAccount = if (accountLower.isNotEmpty()) accountLower else owner

        // Ensure the account exists in the group (covers linked sub-accounts too).
        if (!group.accounts.containsKey(targetAccount)) return null

        return "$uid:$targetAccount"
    }

    private suspend fun resolveOwnerLoginUsernameByUid(uid: Long): String? {
        val raw = AppState.storage.get(JsonBucket.UID_INDEX, uid.toString()) ?: return null
        return try {
            json.decodeFromString(UidIndexRecord.serializer(), raw).loginUsername
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun resolveOwnerLoginUsername(nameLower: String): String? {
        // If the name is itself a login group key, prefer that.
        val direct = AppState.storage.get(JsonBucket.LOGIN_DETAILS, nameLower)
        if (direct != null) {
            // Ensure index exists for the login name.
            upsertAccountIndex(nameLower, nameLower)
            return nameLower
        }

        val idxJson = AppState.storage.get(JsonBucket.ACCOUNT_INDEX, nameLower) ?: return null
        return try {
            json.decodeFromString(AccountIndexRecord.serializer(), idxJson).loginUsername
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun loadGroup(loginUsernameLower: String): StoredAccountGroupRecord? {
        val raw = AppState.storage.get(JsonBucket.LOGIN_DETAILS, loginUsernameLower) ?: return null
        val decoded = try {
            json.decodeFromString(StoredAccountGroupRecord.serializer(), raw)
        } catch (_: Throwable) {
            null
        } ?: return null

        // Backfill uid for older records (set once, then persisted).
        val group =
            if (decoded.uid == 0L) {
                val patched = decoded.copy(uid = stableUidFromLoginUsername(loginUsernameLower))
                AppState.storage.upsert(JsonBucket.LOGIN_DETAILS, loginUsernameLower, json.encodeToString(StoredAccountGroupRecord.serializer(), patched))
                patched
            } else {
                decoded
            }

        // Ensure uid index exists (helps endpoints that only have uid).
        upsertUidIndex(group.uid, loginUsernameLower)

        // Ensure index for all accounts.
        upsertAccountIndex(loginUsernameLower, loginUsernameLower)
        for (k in group.accounts.keys) upsertAccountIndex(k, loginUsernameLower)
        return group
    }

    private suspend fun createGroup(
        loginUsernameLower: String,
        password: String,
        firstAccountLower: String,
        xteas: List<Int>
    ): StoredAccountGroupRecord {
        val now = System.currentTimeMillis()
        val details =
            StoredAccountRecord(
                previousXteas = xteas,
                previousDisplayName = "",
                dateChanged = -1,
                registryDate = now
            )
        val hash = BCrypt.withDefaults().hashToString(12, password.toCharArray())
        val group = StoredAccountGroupRecord(
            loginUsername = loginUsernameLower,
            uid = stableUidFromLoginUsername(loginUsernameLower),
            createdAt = now,
            lastLogin = now,
            passwordHash = hash,
            accounts = mapOf(firstAccountLower to details)
        )
        AppState.storage.upsert(JsonBucket.LOGIN_DETAILS, loginUsernameLower, json.encodeToString(StoredAccountGroupRecord.serializer(), group))
        upsertAccountIndex(firstAccountLower, loginUsernameLower)
        upsertUidIndex(group.uid, loginUsernameLower)
        return group
    }

    private suspend fun upsertAccountIndex(accountLower: String, loginUsernameLower: String) {
        val rec = AccountIndexRecord(account = accountLower, loginUsername = loginUsernameLower)
        AppState.storage.upsert(JsonBucket.ACCOUNT_INDEX, accountLower, json.encodeToString(AccountIndexRecord.serializer(), rec))
    }

    private suspend fun upsertUidIndex(uid: Long, loginUsernameLower: String) {
        if (uid == 0L) return
        val rec = UidIndexRecord(uid = uid, loginUsername = loginUsernameLower)
        AppState.storage.upsert(JsonBucket.UID_INDEX, uid.toString(), json.encodeToString(UidIndexRecord.serializer(), rec))
    }

    private fun Map<String, StoredAccountRecord>.toPlayerDetailsList(uid: PlayerUID): List<PlayerDetailsDto> =
        entries
            .sortedBy { it.key }
            .map { (username, rec) ->
                PlayerDetailsDto(
                    username = username,
                    uid = uid,
                    previousXteas = rec.previousXteas,
                    previousDisplayName = rec.previousDisplayName,
                    dateChanged = rec.dateChanged,
                    registryDate = rec.registryDate
                )
            }
}
