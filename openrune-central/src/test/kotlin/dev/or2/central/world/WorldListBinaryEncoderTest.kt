package dev.or2.central.world

import dev.or2.central.http.world.WorldListBinaryEncoder
import dev.or2.central.http.world.WorldRow
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.nio.charset.Charset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class WorldListBinaryEncoderTest {
    private val cp1252: Charset = Charset.forName("Windows-1252")

    @Test
    fun encodeMatchesRsproxLayout() {
        val worlds =
            listOf(
                WorldRow(1, "MEMBERS", "127.0.0.1", "OpenRune", 0, -1),
                WorldRow(2, "", "host.example", "Activity", 3, 1000),
            )

        val encoded = WorldListBinaryEncoder.encode(worlds)

        val buf = Unpooled.wrappedBuffer(encoded)
        val payloadSize = buf.readInt()
        val count = buf.readUnsignedShort()
        assertEquals(2, count)

        assertWorld(buf, 1, 0x1, "127.0.0.1", "OpenRune", 0, -1)
        assertWorld(buf, 2, 0, "host.example", "Activity", 3, 1000)

        assertEquals(payloadSize + Int.SIZE_BYTES, buf.readerIndex())
    }

    private fun assertWorld(
        buf: ByteBuf,
        id: Int,
        props: Int,
        host: String,
        activity: String,
        location: Int,
        population: Int,
    ) {
        assertEquals(id, buf.readUnsignedShort())
        assertEquals(props, buf.readInt())
        assertEquals(host, readJString(buf))
        assertEquals(activity, readJString(buf))
        assertEquals(location, buf.readUnsignedByte().toInt())
        assertEquals(population, buf.readShort().toInt())
    }

    private fun readJString(buf: ByteBuf): String {
        val len = buf.bytesBefore(0)
        require(len >= 0) { "unterminated string" }
        val s = buf.toString(buf.readerIndex(), len, cp1252)
        buf.skipBytes(len + 1)
        return s
    }

    @Test
    fun rejectsPopulationOutOfShortRange() {
        assertFailsWith<IllegalArgumentException> {
            WorldListBinaryEncoder.encode(
                listOf(WorldRow(1, "", "h", "a", 0, Int.MAX_VALUE)),
            )
        }
    }
}
