package dev.openrune.central.storage

enum class JsonBucket(val defaultIdField: String) {
    PUNISHMENTS("id"),
    PLAYER_SAVES("id"),
    LOGIN_DETAILS("id"),
    ACCOUNT_INDEX("id"),
    UID_INDEX("id"),
    ONLINE_INFO("id"),
    OFFLINE_INFO("id"),
    PLAYERS_ONLINE_HISTORY("id"),
    LOGS("id")
}

