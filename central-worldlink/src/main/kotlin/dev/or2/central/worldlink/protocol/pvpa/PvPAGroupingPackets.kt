package dev.or2.central.worldlink.protocol.pvpa

import dev.or2.central.worldlink.protocol.FrameReader
import dev.or2.central.worldlink.protocol.FrameWriter
import dev.or2.central.worldlink.protocol.WorldOpcodes
import dev.or2.central.worldlink.protocol.outboundFrame

/** Wire representation keeps queue-state separate from clan rank; no guessed icon values. */
object PvPAGroupingPackets {
    fun encodeCommand(command: PvPAGroupingCommand): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_PVPA_GROUPING) {
            writeByte(command.action)
            writeInt(command.characterId)
            writeByte(command.mode)
            writeByte(command.region)
            writeUtf8Truncated(command.displayName, PvPAGroupingWire.MAX_DISPLAY_NAME_UTF8)
        }

    fun decodeCommand(input: FrameReader): PvPAGroupingCommand {
        val command = PvPAGroupingCommand(
            action = input.readUnsignedByte(), characterId = input.readInt(),
            mode = input.readUnsignedByte(), region = input.readUnsignedByte(),
            displayName = input.readUtf8LenPrefixed(),
        )
        input.requireFullyConsumed()
        require(command.action in PvPAGroupingWire.LOAD..PvPAGroupingWire.RESTORE)
        require(command.characterId > 0)
        require(command.displayName.toByteArray(Charsets.UTF_8).size <= PvPAGroupingWire.MAX_DISPLAY_NAME_UTF8)
        return command
    }

    fun encodeResult(result: PvPAGroupingResult): ByteArray =
        outboundFrame(WorldOpcodes.OP_WORLD_PVPA_GROUPING_RESULT) {
            writeByte(result.action)
            writeByte(result.status)
            writeInt(result.currentMember?.characterId ?: 0)
            writeSnapshotOrEmpty(result.snapshot)
        }

    fun decodeResult(input: FrameReader): PvPAGroupingResult {
        val action = input.readUnsignedByte()
        val status = input.readUnsignedByte()
        val memberId = input.readInt()
        val snapshot = input.readSnapshotOrEmpty()
        input.requireFullyConsumed()
        val member = if (memberId > 0) snapshot?.members?.firstOrNull { it.characterId == memberId } else null
        return PvPAGroupingResult(action, status, snapshot, member)
    }

    fun encodeRoster(snapshot: PvPAGroupSnapshot): ByteArray =
        outboundFrame(WorldOpcodes.OP_SERVER_PVPA_GROUPING_ROSTER) { writeSnapshotOrEmpty(snapshot) }

    fun decodeRoster(input: FrameReader): PvPAGroupSnapshot {
        val snapshot = input.readSnapshotOrEmpty() ?: error("Roster cannot be empty")
        input.requireFullyConsumed()
        return snapshot
    }

    private fun FrameWriter.writeSnapshotOrEmpty(snapshot: PvPAGroupSnapshot?) {
        writeByte(if (snapshot == null) 0 else 1)
        if (snapshot == null) return
        require(snapshot.members.size <= PvPAGroupingWire.MAX_MEMBERS)
        writeByte(PvPAGroupingPresentation.modeCode(snapshot.key.mode))
        writeByte(snapshot.key.preferredRegion)
        writeLong(snapshot.revision)
        writeShort(snapshot.members.size)
        snapshot.members.forEach { member ->
            writeInt(member.characterId)
            writeUtf8Truncated(member.displayName, PvPAGroupingWire.MAX_DISPLAY_NAME_UTF8)
            writeInt(member.worldId)
            writeByte(PvPAGroupingPresentation.stateCode(member.state))
        }
    }

    private fun FrameReader.readSnapshotOrEmpty(): PvPAGroupSnapshot? {
        if (readUnsignedByte() == 0) return null
        val mode = PvPAGroupingPresentation.modeFromCode(readUnsignedByte()) ?: error("Bad PvPA mode")
        val region = readUnsignedByte()
        val revision = readLong()
        val count = readUnsignedShort()
        require(region in 0..2 && count <= PvPAGroupingWire.MAX_MEMBERS && revision >= 0)
        val members = List(count) {
            val id = readInt()
            val name = readUtf8LenPrefixed()
            val world = readInt()
            val state = when (readUnsignedByte()) {
                PvPAGroupingWire.GUEST -> PvPAQueueState.GUEST
                PvPAGroupingWire.QUEUED -> PvPAQueueState.SIGNED_UP
                else -> error("Bad PvPA state")
            }
            require(id > 0 && world > 0 && name.isNotBlank() &&
                name.toByteArray(Charsets.UTF_8).size <= PvPAGroupingWire.MAX_DISPLAY_NAME_UTF8)
            PvPAGroupMember(id, name, world, state)
        }
        return PvPAGroupSnapshot(PvPAGroupKey(mode, region), revision, members)
    }
}
