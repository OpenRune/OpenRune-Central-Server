package dev.openrune.central.logging

import kotlinx.serialization.Serializable

@Serializable
enum class PlayerRights {
    PLAYER,
    MODERATOR,
    ADMIN
}

