// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.songformat

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * A bounded ZIP reader for release archives and snapshots (song-format 12.2, 12.7 step 1, 12.9).
 *
 * Everything is decided from the central directory before an entry is inflated: encryption and
 * compression method (A004), special entries (A005), entry names and duplicates, also by case (A006),
 * entry count (A002), and the uncompressed total against the absolute and ratio limits (A003).
 * Inflating enforces the declared size and CRC again. Entry names are only ever map keys, never
 * file-system paths. Multi-disk archives are refused (A001); ZIP64 sizes and offsets are read.
 */
internal class ZipReader private constructor(private val zip: ByteArray, val entries: List<Entry>) {

    class Entry(
        val name: String,
        val method: Int,
        val crc: Long,
        val compressedSize: Long,
        val size: Long,
        val localHeaderOffset: Long,
    ) {
        val isDirectory: Boolean get() = name.endsWith("/")
    }

    class Refused(val code: String, message: String) : Exception(message)

    /** The bytes of [entry], inflated within its declared size and checked against its CRC. */
    fun read(entry: Entry): ByteArray {
        val p = entry.localHeaderOffset
        if (p < 0 || p + LOCAL_HEADER > zip.size || u32(p.toInt()) != LOCAL_SIG) throw Refused("A001", "${entry.name}: no local header")
        val nameLen = u16(p.toInt() + 26)
        val extraLen = u16(p.toInt() + 28)
        val start = p + LOCAL_HEADER + nameLen + extraLen
        val localName = zip.copyOfRange(p.toInt() + LOCAL_HEADER, minOf(zip.size, p.toInt() + LOCAL_HEADER + nameLen))
        if (!localName.contentEquals(entry.name.toByteArray(Charsets.UTF_8))) throw Refused("A006", "${entry.name}: local name differs from the directory")
        if (start + entry.compressedSize > zip.size) throw Refused("A001", "${entry.name}: data beyond the end")
        val data = zip.copyOfRange(start.toInt(), (start + entry.compressedSize).toInt())
        val out = when (entry.method) {
            STORED -> { if (entry.compressedSize != entry.size) throw Refused("A001", "${entry.name}: stored sizes differ"); data }
            DEFLATED -> inflate(entry, data)
            else -> throw Refused("A004", "${entry.name}: method ${entry.method}")
        }
        val crc = CRC32().apply { update(out) }.value
        if (crc != entry.crc) throw Refused("A001", "${entry.name}: CRC mismatch")
        return out
    }

    private fun inflate(entry: Entry, data: ByteArray): ByteArray {
        val out = ByteArray(entry.size.toInt())
        val inflater = Inflater(true)
        try {
            inflater.setInput(data)
            var n = 0
            while (!inflater.finished()) {
                if (n == out.size) {
                    // Declared size reached but the stream goes on: a bomb that lied in the directory (12.9).
                    val probe = ByteArray(1)
                    if (inflater.inflate(probe) > 0) throw Refused("A003", "${entry.name}: inflates beyond its declared ${entry.size} bytes")
                    if (!inflater.finished()) throw Refused("A001", "${entry.name}: truncated deflate stream")
                    break
                }
                val got = inflater.inflate(out, n, out.size - n)
                if (got == 0 && (inflater.needsInput() || inflater.needsDictionary())) throw Refused("A001", "${entry.name}: truncated deflate stream")
                n += got
            }
            if (n != out.size) throw Refused("A001", "${entry.name}: inflates to $n of ${entry.size} bytes")
            return out
        } catch (e: DataFormatException) {
            throw Refused("A001", "${entry.name}: bad deflate data")
        } finally {
            inflater.end()
        }
    }

    private fun u16(at: Int): Int = (zip[at].toInt() and 0xFF) or ((zip[at + 1].toInt() and 0xFF) shl 8)
    private fun u32(at: Int): Long = (u16(at).toLong()) or (u16(at + 2).toLong() shl 16)

    companion object {
        const val STORED = 0
        const val DEFLATED = 8
        private const val LOCAL_SIG = 0x04034b50L
        private const val CENTRAL_SIG = 0x02014b50L
        private const val END_SIG = 0x06054b50L
        private const val END64_LOCATOR_SIG = 0x07064b50L
        private const val END64_SIG = 0x06064b50L
        private const val LOCAL_HEADER = 30
        private const val CENTRAL_HEADER = 46
        private const val END_RECORD = 22
        private const val MAX_COMMENT = 0xFFFF

        /** Reads and checks the central directory; throws [Refused] with the 12.8 code. */
        fun open(zip: ByteArray): ZipReader {
            if (zip.size > Limits.ARCHIVE_MAX_BYTES) throw Refused("A002", "${zip.size} bytes")
            val r = Raw(zip)
            val end = (zip.size - END_RECORD downTo maxOf(0, zip.size - END_RECORD - MAX_COMMENT)).firstOrNull { r.u32(it) == END_SIG }
                ?: throw Refused("A001", "not a ZIP file")
            if (r.u16(end + 4) != 0 || r.u16(end + 6) != 0) throw Refused("A001", "multi-disk archive")
            var count = r.u16(end + 10).toLong()
            var cdSize = r.u32(end + 12)
            var cdOffset = r.u32(end + 16)
            if (count == 0xFFFFL || cdSize == 0xFFFFFFFFL || cdOffset == 0xFFFFFFFFL) {
                val loc = end - 20
                if (loc < 0 || r.u32(loc) != END64_LOCATOR_SIG) throw Refused("A001", "ZIP64 locator missing")
                val e64 = r.u64(loc + 8)
                if (e64 < 0 || e64 + 56 > zip.size || r.u32(e64.toInt()) != END64_SIG) throw Refused("A001", "ZIP64 end record missing")
                count = r.u64(e64.toInt() + 32)
                cdSize = r.u64(e64.toInt() + 40)
                cdOffset = r.u64(e64.toInt() + 48)
            }
            if (count > Limits.ARCHIVE_MAX_ENTRIES) throw Refused("A002", "$count entries")
            if (cdOffset < 0 || cdSize < 0 || cdOffset + cdSize > zip.size) throw Refused("A001", "central directory beyond the end")

            val entries = ArrayList<Entry>()
            val seen = HashSet<String>()
            var total = 0L
            var p = cdOffset.toInt()
            repeat(count.toInt()) {
                if (p + CENTRAL_HEADER > zip.size || r.u32(p) != CENTRAL_SIG) throw Refused("A001", "central directory corrupt")
                val madeBy = r.u16(p + 4)
                val flags = r.u16(p + 8)
                val method = r.u16(p + 10)
                val crc = r.u32(p + 16)
                var csize = r.u32(p + 20)
                var usize = r.u32(p + 24)
                val nameLen = r.u16(p + 28)
                val extraLen = r.u16(p + 30)
                val commentLen = r.u16(p + 32)
                val external = r.u32(p + 38)
                var offset = r.u32(p + 42)
                val next = p + CENTRAL_HEADER + nameLen + extraLen + commentLen
                if (next > zip.size) throw Refused("A001", "central directory corrupt")
                val rawName = zip.copyOfRange(p + CENTRAL_HEADER, p + CENTRAL_HEADER + nameLen)
                // ZIP64 extra field (0x0001): the sizes and offset that read as 0xFFFFFFFF, in this order.
                var x = p + CENTRAL_HEADER + nameLen
                val xEnd = x + extraLen
                while (x + 4 <= xEnd) {
                    val id = r.u16(x)
                    val len = r.u16(x + 2)
                    if (x + 4 + len > xEnd) break
                    if (id == 0x0001) {
                        var f = x + 4
                        if (usize == 0xFFFFFFFFL && f + 8 <= x + 4 + len) { usize = r.u64(f); f += 8 }
                        if (csize == 0xFFFFFFFFL && f + 8 <= x + 4 + len) { csize = r.u64(f); f += 8 }
                        if (offset == 0xFFFFFFFFL && f + 8 <= x + 4 + len) { offset = r.u64(f) }
                    }
                    x += 4 + len
                }
                val name = entryName(rawName, flags)
                if ((flags and 0x0001) != 0 || (flags and 0x0040) != 0) throw Refused("A004", "$name is encrypted")
                if (method != STORED && method != DEFLATED) throw Refused("A004", "$name uses method $method")
                val host = madeBy ushr 8
                val mode = (external ushr 16) and 0xFFFF
                val type = mode and 0xF000
                if ((host == 3 || host == 19) && mode != 0L && type != 0x8000L && type != 0x4000L) throw Refused("A005", "$name is a special entry")
                if (!seen.add(name.lowercase(Locale.ROOT))) throw Refused("A006", "$name occurs twice")
                if (csize < 0 || usize < 0) throw Refused("A001", "$name: bad sizes")
                total += usize
                entries += Entry(name, method, crc, csize, usize, offset)
                p = next
            }
            if (total > Limits.ARCHIVE_UNCOMPRESSED_MAX_BYTES) throw Refused("A003", "$total bytes uncompressed")
            if (total > Limits.ARCHIVE_MAX_RATIO.toLong() * zip.size) throw Refused("A003", "$total bytes from ${zip.size}: more than ${Limits.ARCHIVE_MAX_RATIO} times")
            return ZipReader(zip, entries)
        }

        /** 12.2: UTF-8 (strict), ≤ 255 bytes, relative, `/` only, no `.` or `..`, no drive letter. */
        private fun entryName(raw: ByteArray, flags: Int): String {
            if (raw.size > Limits.ARCHIVE_ENTRY_NAME_MAX_BYTES) throw Refused("A006", "a name of ${raw.size} bytes")
            val ascii = raw.all { it >= 0 }
            if (!ascii && (flags and 0x0800) == 0) throw Refused("A006", "a name that is not marked UTF-8")
            val name = try {
                Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString()
            } catch (e: CharacterCodingException) {
                throw Refused("A006", "a name that is not UTF-8")
            }
            val parts = name.removeSuffix("/").split('/')
            val bad = name.isEmpty() || name.startsWith("/") || '\\' in name || '\u0000' in name ||
                Regex("^[A-Za-z]:").containsMatchIn(name) || parts.any { it.isEmpty() || it == "." || it == ".." }
            if (bad) throw Refused("A006", "name $name")
            return name
        }
    }

    private class Raw(val b: ByteArray) {
        fun u16(at: Int): Int = if (at < 0 || at + 2 > b.size) throw Refused("A001", "truncated") else (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)
        fun u32(at: Int): Long = u16(at).toLong() or (u16(at + 2).toLong() shl 16)
        fun u64(at: Int): Long {
            val v = u32(at) or (u32(at + 4) shl 32)
            if (v < 0) throw Refused("A001", "size beyond range")
            return v
        }
    }
}
