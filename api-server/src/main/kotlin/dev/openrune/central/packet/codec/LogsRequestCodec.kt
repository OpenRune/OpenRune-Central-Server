package dev.openrune.central.packet.codec

import dev.openrune.central.AppState
import dev.openrune.central.LogsResponse
import dev.openrune.central.logging.Loggable
import dev.openrune.central.logging.LoggingJson
import dev.openrune.central.packet.IncomingPacketHandler
import dev.openrune.central.packet.PacketCallContext
import dev.openrune.central.packet.io.PacketReader
import dev.openrune.central.packet.io.PacketWriter
import dev.openrune.central.packet.model.LogsRequestIncoming
import dev.openrune.central.packet.model.LogsResponseOutgoing
import dev.openrune.central.storage.JsonBucket
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

object LogsRequestCodec : PacketCodec<LogsRequestIncoming>, IncomingPacketHandler<LogsRequestIncoming> {

    private val logger = LoggerFactory.getLogger(LogsRequestCodec::class.java)

    override fun decodeBody(r: PacketReader, requestId: Long): LogsRequestIncoming {
        val data = r.readString()
        return LogsRequestIncoming(data = data, requestId = requestId)
    }

    override fun encodeBody(w: PacketWriter, body: LogsRequestIncoming) {
        w.writeString(body.data)
    }

    override fun handle(call: PacketCallContext, body: LogsRequestIncoming) {
        val result = runBlocking {
            try {
                // Only allow known Loggable subtypes (decode will fail for unknown discriminator)
                val decoded = LoggingJson.json.decodeFromString(Loggable.serializer(), body.data)
                val normalized = LoggingJson.json.encodeToString(Loggable.serializer(), decoded)
                AppState.storage.append(JsonBucket.LOGS, decoded.logType, normalized)
                LogsResponseOutgoing(result = LogsResponse.SUCCESS, error = null, requestId = body.requestId)
            } catch (t: Throwable) {
                logger.warn("logs: decode/append failed (world=${call.worldId}, bodyLen=${body.data.length})", t)
                LogsResponseOutgoing(
                    result = LogsResponse.FAILED,
                    error = "unknown/invalid log type",
                    requestId = body.requestId
                )
            }
        }

        call.respond(result)
    }
}

