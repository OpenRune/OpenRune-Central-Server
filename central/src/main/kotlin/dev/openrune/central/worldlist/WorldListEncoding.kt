package dev.openrune.central.worldlist

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import dev.openrune.central.buffer.JagByteBuf
import dev.openrune.central.buffer.extensions.toJagByteBuf
import dev.openrune.central.config.WorldConfig
import dev.openrune.central.world.WorldManager

internal fun encodeWorldList(worlds: List<WorldConfig>, allocator: ByteBufAllocator): JagByteBuf {
    val buffer = allocator.buffer().toJagByteBuf()
    val pos = buffer.writerIndex()
    buffer.p4(0)
    buffer.p2(worlds.size)
    for (world in worlds) {

        var mask = 0
        world.types.forEach { type -> mask = mask or type.mask }

        buffer.p2(world.id)
        buffer.p4(mask)
        val online = WorldManager.isWorldOnline(world.id)
        buffer.pjstr(world.address)
        buffer.pjstr(if (online) world.activity else "OFFLINE")
        buffer.p1(world.location.id)
        buffer.p2(if (online) WorldManager.onlineCount(world.id) else -1)
    }
    val end = buffer.writerIndex()
    buffer.writerIndex(pos)
    buffer.p4(end - pos - Int.SIZE_BYTES)
    buffer.writerIndex(end)
    return buffer
}


/**
 * Writes a typical "Jagex string": UTF-8 bytes followed by a 0 terminator.
 */
private fun ByteBuf.writeString(value: String) {
    val bytes = value.toByteArray()
    writeBytes(bytes).writeByte(0)
}

