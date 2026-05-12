package dev.or2.login.model

/** Portable login outcome for game + HTTP (not tied to rsprot types). */
public sealed class OpenRuneLoginOutcome {
    public abstract val code: String

    public data class Ok(
        val slot: Int,
        val staffModLevel: Int,
        val playerMod: Boolean,
        val member: Boolean,
        val accountHash: Long,
        val userId: Long,
        val userHash: Long,
        val authenticatorKind: String,
    ) : OpenRuneLoginOutcome() {
        override val code: String = "ok"
    }

    public sealed class Denied : OpenRuneLoginOutcome() {
        public data object InvalidCredentials : Denied() {
            override val code: String = "invalid_credentials"
        }

        public data object Banned : Denied() {
            override val code: String = "banned"
        }

        public data object Locked : Denied() {
            override val code: String = "locked"
        }

        public data object ServerFull : Denied() {
            override val code: String = "server_full"
        }

        public data object Duplicate : Denied() {
            override val code: String = "duplicate"
        }

        public data object Timeout : Denied() {
            override val code: String = "timeout"
        }

        public data object UpdateInProgress : Denied() {
            override val code: String = "update_in_progress"
        }

        public data object LoginServerOffline : Denied() {
            override val code: String = "login_server_offline"
        }

        public data object LoginServerNoReply : Denied() {
            override val code: String = "login_server_no_reply"
        }

        public data object LoginServerLoadError : Denied() {
            override val code: String = "login_server_load_error"
        }

        public data object UnknownReplyFromLoginServer : Denied() {
            override val code: String = "unknown_reply_from_login_server"
        }

        public data object InvalidAuthenticatorCode : Denied() {
            override val code: String = "invalid_authenticator_code"
        }

        public data object AuthenticatorRequired : Denied() {
            override val code: String = "authenticator_required"
        }

        public data object InvalidSave : Denied() {
            override val code: String = "invalid_save"
        }

        public data object ConnectFail : Denied() {
            override val code: String = "connect_fail"
        }

        public data class DisallowedByScript(
            val line1: String,
            val line2: String,
            val line3: String,
        ) : Denied() {
            override val code: String = "disallowed_by_script"
        }

        public data class Unmapped(
            val rsprotKind: String,
        ) : Denied() {
            override val code: String = "unmapped"
        }
    }

    public companion object {
        public fun toBriefMap(outcome: OpenRuneLoginOutcome): Map<String, Any?> =
            when (outcome) {
                is Ok ->
                    mapOf(
                        "code" to outcome.code,
                        "slot" to outcome.slot,
                        "staff_mod_level" to outcome.staffModLevel,
                        "player_mod" to outcome.playerMod,
                        "member" to outcome.member,
                        "account_hash" to outcome.accountHash,
                        "user_id" to outcome.userId,
                        "user_hash" to outcome.userHash,
                        "authenticator" to outcome.authenticatorKind,
                    )
                is Denied.DisallowedByScript ->
                    mapOf(
                        "code" to outcome.code,
                        "line1" to outcome.line1,
                        "line2" to outcome.line2,
                        "line3" to outcome.line3,
                    )
                is Denied.Unmapped ->
                    mapOf(
                        "code" to outcome.code,
                        "rsprot_kind" to outcome.rsprotKind,
                    )
                is Denied ->
                    mapOf(
                        "code" to outcome.code,
                    )
            }
    }
}
