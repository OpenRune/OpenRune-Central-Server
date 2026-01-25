package dev.openrune.central.packet.io

/**
 * Field types for packet bodies.
 *
 * Sizes:
 * - Fixed-size fields have a constant [minSize] and [fixedSize].
 * - Variable-size fields (STRING, BYTES) have [fixedSize] = null and include a length prefix.
 */
enum class FieldType(
    val minSize: Int,
    val fixedSize: Int? = minSize
) {
    BOOLEAN(1),
    BYTE(1),
    SHORT(2),
    INT(4),
    LONG(8),

    /**
     * UTF-8 string with an unsigned u16 byte-length prefix.
     * Minimum size is 2 bytes (length=0).
     */
    STRING(2, fixedSize = null),

    /**
     * Raw bytes with an unsigned u32 length prefix.
     * Minimum size is 4 bytes (length=0).
     */
    BYTES(4, fixedSize = null),

    /**
     * List<Int> with a u8 count prefix, followed by count * int32.
     * Minimum size is 1 byte (count=0).
     */
    INT_LIST_U8(1, fixedSize = null),
}

