package app.gamenative.wow.solo

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches folders of AzerothCore's client-data zip without downloading all of it: reads the zip's file list,
 * then streams only the byte ranges of the folders asked for. Each file is checked against the sha256 list the
 * solo-server workflow made from the pinned release, so a changed or damaged file is refused.
 */
internal class ClientData(private val http: OkHttpClient, private val url: String) {
    data class Entry(val name: String, val method: Int, val compressedSize: Long, val size: Long, val offset: Long) {
        val folder get() = name.substringBefore('/')
    }

    /** Hashes from the workflow: path to (size, sha256). */
    class Hashes(val byPath: Map<String, Pair<Long, String>>) {
        companion object {
            fun parse(text: String) = Hashes(
                text.lineSequence().filter { it.isNotBlank() }.associate { line ->
                    val (hash, size, path) = line.split(Regex("\\s+"), limit = 3)
                    path to (size.toLong() to hash)
                },
            )
        }
    }

    fun entries(): List<Entry> {
        val length = contentLength()
        val tailSize = minOf(length, 70_000L)
        val tail = range(length - tailSize, length - 1)
        val eocd = (tail.size - 22 downTo 0).firstOrNull { le32(tail, it) == 0x06054b50L } ?: throw IOException("Not a zip file.")
        var cdSize = le32(tail, eocd + 12)
        var cdOffset = le32(tail, eocd + 16)
        var count = le16(tail, eocd + 10).toLong()
        val locator = eocd - 20
        if (locator >= 0 && le32(tail, locator) == 0x07064b50L) {
            val z64 = le64(tail, locator + 8)
            val rec = range(z64, z64 + 55)
            count = le64(rec, 32)
            cdSize = le64(rec, 40)
            cdOffset = le64(rec, 48)
        }
        return parseCentralDirectory(range(cdOffset, cdOffset + cdSize - 1), count)
    }

    /**
     * Writes every file of [folders] under [dest], skipping files already there at the right size (they were only
     * moved into place after their hash matched). [onBytes] gets compressed bytes read, for progress.
     */
    fun fetch(entries: List<Entry>, folders: Set<String>, hashes: Hashes, dest: File, onBytes: (Long) -> Unit) {
        val wanted = entries.filter { it.folder in folders && !it.name.endsWith("/") }.sortedBy { it.offset }
        var stream: CountingStream? = null
        try {
            for (e in wanted) {
                val (size, sha) = hashes.byPath[e.name] ?: throw IOException("${e.name} is not in the pinned client data.")
                val out = safeChild(dest, e.name)
                if (out.length() == size && size == e.size) {
                    onBytes(e.compressedSize)
                    continue
                }
                var s = stream
                if (s == null || e.offset < s.position || e.offset - s.position > MAX_SKIP) {
                    s?.close()
                    s = CountingStream(openFrom(e.offset), e.offset)
                    stream = s
                }
                s.skipTo(e.offset)
                val header = s.readExactly(30)
                if (le32(header, 0) != 0x04034b50L) throw IOException("Bad zip entry at ${e.offset}.")
                s.skipTo(s.position + le16(header, 26) + le16(header, 28))
                writeEntry(s, e, out, size, sha)
                onBytes(e.compressedSize)
            }
        } finally {
            stream?.close()
        }
    }

    private fun writeEntry(s: CountingStream, e: Entry, out: File, size: Long, sha: String) {
        out.parentFile?.mkdirs()
        val tmp = File(out.path + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        val body = s.limited(e.compressedSize)
        val data: InputStream = when (e.method) {
            0 -> body
            8 -> InflaterInputStream(body, Inflater(true), 1 shl 16)
            else -> throw IOException("${e.name} uses an unsupported zip method ${e.method}.")
        }
        var written = 0L
        tmp.outputStream().use { o ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = data.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
                o.write(buf, 0, n)
                written += n
            }
        }
        body.drain()
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        if (written != size || hex != sha) {
            tmp.delete()
            throw IOException("${e.name} doesn't match the pinned client data.")
        }
        if (!tmp.renameTo(out)) throw IOException("Couldn't write ${out.path}.")
    }

    private fun contentLength(): Long =
        http.newCall(Request.Builder().url(url).head().build()).execute().use { rsp ->
            if (!rsp.isSuccessful) throw IOException("Client data: HTTP ${rsp.code}")
            rsp.header("Content-Length")?.toLongOrNull() ?: throw IOException("Client data has no size.")
        }

    private fun range(from: Long, to: Long): ByteArray =
        http.newCall(Request.Builder().url(url).header("Range", "bytes=$from-$to").build()).execute().use { rsp ->
            if (rsp.code != 206) throw IOException("Client data: range request answered ${rsp.code}")
            rsp.body!!.bytes()
        }

    private fun openFrom(from: Long): InputStream {
        val rsp = http.newCall(Request.Builder().url(url).header("Range", "bytes=$from-").build()).execute()
        if (rsp.code != 206) {
            rsp.close()
            throw IOException("Client data: range request answered ${rsp.code}")
        }
        return rsp.body!!.byteStream().buffered(1 shl 20)
    }

    private class CountingStream(private val input: InputStream, var position: Long) : AutoCloseable {
        fun skipTo(target: Long) {
            while (position < target) {
                val n = input.skip(target - position)
                if (n <= 0) {
                    if (input.read() < 0) throw IOException("Client data ended early.")
                    position++
                } else {
                    position += n
                }
            }
        }

        fun readExactly(n: Int): ByteArray {
            val b = ByteArray(n)
            var off = 0
            while (off < n) {
                val r = input.read(b, off, n - off)
                if (r < 0) throw IOException("Client data ended early.")
                off += r
            }
            position += n
            return b
        }

        fun limited(count: Long) = Limited(count)

        inner class Limited(private var left: Long) : InputStream() {
            override fun read(): Int {
                if (left <= 0) return -1
                val b = input.read()
                if (b < 0) throw IOException("Client data ended early.")
                left--
                position++
                return b
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (left <= 0) return -1
                val n = input.read(b, off, minOf(len.toLong(), left).toInt())
                if (n < 0) throw IOException("Client data ended early.")
                left -= n
                position += n
                return n
            }

            fun drain() {
                skipTo(position + left)
                left = 0
            }
        }

        override fun close() = input.close()
    }

    companion object {
        const val PATHING = "mmaps"
        private val CORE = setOf("dbc", "maps", "vmaps", "Cameras")
        private const val MAX_SKIP = 8L shl 20

        fun folders(pathing: Boolean) = if (pathing) CORE + PATHING else CORE

        internal fun parseCentralDirectory(cd: ByteArray, count: Long): List<Entry> {
            val list = ArrayList<Entry>(count.toInt())
            var p = 0
            while (p + 46 <= cd.size && le32(cd, p) == 0x02014b50L) {
                val method = le16(cd, p + 10)
                var compressed = le32(cd, p + 20)
                var size = le32(cd, p + 24)
                val nameLen = le16(cd, p + 28)
                val extraLen = le16(cd, p + 30)
                val commentLen = le16(cd, p + 32)
                var offset = le32(cd, p + 42)
                val name = String(cd, p + 46, nameLen, Charsets.UTF_8)
                // Zip64 sizes and offset, in that order, for the fields set to 0xFFFFFFFF.
                var x = p + 46 + nameLen
                val extraEnd = x + extraLen
                while (x + 4 <= extraEnd) {
                    val id = le16(cd, x)
                    val len = le16(cd, x + 2)
                    if (id == 1) {
                        var f = x + 4
                        if (size == 0xFFFFFFFFL) { size = le64(cd, f); f += 8 }
                        if (compressed == 0xFFFFFFFFL) { compressed = le64(cd, f); f += 8 }
                        if (offset == 0xFFFFFFFFL) offset = le64(cd, f)
                    }
                    x += 4 + len
                }
                list += Entry(name, method, compressed, size, offset)
                p = extraEnd + commentLen
            }
            return list
        }

        internal fun safeChild(dest: File, name: String): File {
            if (name.startsWith("/") || name.split('/').any { it == ".." }) throw IOException("Unsafe path in client data: $name")
            return File(dest, name)
        }

        private fun le16(b: ByteArray, at: Int) = ByteBuffer.wrap(b, at, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        private fun le32(b: ByteArray, at: Int) = ByteBuffer.wrap(b, at, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
        private fun le64(b: ByteArray, at: Int) = ByteBuffer.wrap(b, at, 8).order(ByteOrder.LITTLE_ENDIAN).long
    }
}
