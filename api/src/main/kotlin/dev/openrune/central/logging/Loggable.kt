package dev.openrune.central.logging

import kotlinx.serialization.Serializable
import java.util.concurrent.ThreadLocalRandom

/**
 * Base for anything that can be sent to the central server logging API.
 *
 * NOTE: This is sealed so only known log types can be decoded server-side.
 */
@Serializable
open class Loggable {

    open var logType: String = this::class.simpleName.toString()
    open var player: String = ""
    open var accessRights: PlayerRights = PlayerRights.PLAYER
    open val dateTime: Long = System.currentTimeMillis()
    open val id: Long = ThreadLocalRandom.current().nextLong()
}