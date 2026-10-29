package app.gamenative.wow.solo

import android.content.Context
import android.os.StatFs
import app.gamenative.utils.Net
import app.gamenative.wow.legacy.LegacyServers
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream

sealed interface SoloSetupState {
    data object Idle : SoloSetupState
    data class Running(val step: String, val fraction: Float?) : SoloSetupState
    data class Failed(val message: String) : SoloSetupState
    data object Done : SoloSetupState
}

/**
 * Sets up the solo server: downloads the pinned server and world database, fetches the map data, and creates
 * the player's account. Each finished step is recorded in [SoloState], so running it again resumes.
 */
object SoloSetup {
    const val ACCOUNT = "SOLO"

    private val _state = MutableStateFlow<SoloSetupState>(SoloSetupState.Idle)
    val state: StateFlow<SoloSetupState> = _state.asStateFlow()

    internal fun update(s: SoloSetupState) {
        _state.value = s
    }

    /** Bytes downloaded and bytes on disk for a setup with or without mob pathing. */
    data class Sizes(val download: Long, val disk: Long)

    fun sizes(manifest: SoloManifest, pathing: Boolean): Sizes {
        val data = if (pathing) CLIENT_DATA_ALL else CLIENT_DATA_CORE
        val download = manifest.server.size + manifest.database.size + if (pathing) CLIENT_ZIP_ALL else CLIENT_ZIP_CORE
        return Sizes(download, manifest.serverUnpacked + manifest.databaseUnpacked + data)
    }

    fun freeSpace(context: Context): Long = runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L)

    internal fun run(context: Context, pathing: Boolean, gm: Boolean) {
        val manifest = SoloManifest.load(context) ?: throw IOException("This version of the app has no solo server build.")
        val paths = SoloPaths.of(context)
        paths.root.mkdirs()
        var state = SoloState.load(paths).copy(gm = gm)
        val needed = sizes(manifest, pathing).disk - onDisk(paths)
        if (needed > freeSpace(context)) throw IOException("Not enough space: setup needs ${gb(needed)} more.")

        if (state.server != manifest.azerothcore) {
            val archive = download(paths, manifest.server, "Downloading the server")
            report("Unpacking the server", null)
            replaceDir(paths.usr) { unpack(archive, it, strip = "usr/") }
            archive.delete()
            state = state.copy(server = manifest.azerothcore).also { SoloState.save(paths, it) }
        }

        // The database holds the player's characters, so a newer server build never replaces it.
        if (state.database.isEmpty() || !paths.db.isDirectory) {
            val archive = download(paths, manifest.database, "Downloading the world database")
            report("Unpacking the world database", null)
            replaceDir(paths.db) { tmp -> unpack(archive, tmp, strip = "db/") }
            archive.delete()
            state = state.copy(database = manifest.azerothcore).also { SoloState.save(paths, it) }
        }

        val folders = ClientData.folders(pathing)
        if (!state.dataFolders.containsAll(folders)) {
            report("Reading the map data list", null)
            val hashesFile = download(paths, manifest.clientDataHashes, "Reading the map data list")
            val hashes = ClientData.Hashes.parse(ZstdCompressorInputStream(hashesFile.inputStream().buffered()).use { it.readBytes().decodeToString() })
            val client = ClientData(Net.http, manifest.clientData.url)
            val entries = client.entries()
            val todo = folders - state.dataFolders
            val total = entries.filter { it.folder in todo }.sumOf { it.compressedSize }.coerceAtLeast(1)
            var done = 0L
            for (folder in todo.sortedBy { if (it == ClientData.PATHING) 1 else 0 }) {
                client.fetch(entries, setOf(folder), hashes, paths.data) { n ->
                    done += n
                    report(if (folder == ClientData.PATHING) "Downloading mob pathing" else "Downloading map data", done.toFloat() / total)
                }
                state = state.copy(dataFolders = state.dataFolders + folder).also { SoloState.save(paths, it) }
            }
            hashesFile.delete()
        }
        if (!pathing && ClientData.PATHING in state.dataFolders) {
            File(paths.data, ClientData.PATHING).deleteRecursively()
            state = state.copy(dataFolders = state.dataFolders - ClientData.PATHING).also { SoloState.save(paths, it) }
        }

        if (state.account.isEmpty()) {
            report("Creating your account", null)
            val id = state.serverId.ifEmpty { UUID.randomUUID().toString().take(8) }
            val password = newPassword()
            kotlinx.coroutines.runBlocking { SoloServer.sql(context, accountSql(ACCOUNT, password, gm)) }
            LegacyServers.vault(context, id).save("", password)
            state = state.copy(account = ACCOUNT, serverId = id).also { SoloState.save(paths, it) }
        }
        paths.downloads.deleteRecursively()
    }

    /** Turns GM commands on or off for the solo account. */
    suspend fun setGm(context: Context, on: Boolean) {
        val paths = SoloPaths.of(context)
        SoloServer.sql(context, gmSql(ACCOUNT, on))
        SoloState.save(paths, SoloState.load(paths).copy(gm = on))
    }

    /** Stops the server and deletes everything setup made, characters included. */
    suspend fun remove(context: Context) {
        SoloServer.stop()
        val paths = SoloPaths.of(context)
        val id = SoloState.load(paths).serverId
        if (id.isNotEmpty()) LegacyServers.vault(context, id).clear()
        paths.root.deleteRecursively()
        _state.value = SoloSetupState.Idle
    }

    fun onDisk(paths: SoloPaths): Long = if (paths.root.exists()) paths.root.walkBottomUp().filter { it.isFile }.sumOf { it.length() } else 0L

    internal fun accountSql(user: String, password: String, gm: Boolean): String {
        val salt = Srp6.newSalt()
        val verifier = Srp6.verifier(user, password, salt)
        return "INSERT INTO acore_auth.account (username, salt, verifier, expansion) VALUES ('$user', X'${hex(salt)}', X'${hex(verifier)}', 2) " +
            "ON DUPLICATE KEY UPDATE salt = VALUES(salt), verifier = VALUES(verifier); " + gmSql(user, gm)
    }

    internal fun gmSql(user: String, on: Boolean): String {
        val clear = "DELETE a FROM acore_auth.account_access a JOIN acore_auth.account c ON c.id = a.id WHERE c.username = '$user';"
        return if (on) "$clear INSERT INTO acore_auth.account_access (id, gmlevel, RealmID) SELECT id, 3, -1 FROM acore_auth.account WHERE username = '$user';" else clear
    }

    private fun report(step: String, fraction: Float?) {
        _state.value = SoloSetupState.Running(step, fraction)
    }

    /** Downloads [part] to the downloads folder, resuming a partial file, and checks its sha256. */
    private fun download(paths: SoloPaths, part: SoloPart, step: String): File {
        paths.downloads.mkdirs()
        val dest = File(paths.downloads, part.url.substringAfterLast('/'))
        if (dest.length() != part.size || sha256(dest) != part.sha256) {
            val partial = File(dest.path + ".part")
            if (partial.length() > part.size) partial.delete()
            val request = Request.Builder().url(part.url).apply { if (partial.length() > 0) header("Range", "bytes=${partial.length()}-") }.build()
            Net.http.newBuilder().callTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS).build().newCall(request).execute().use { rsp ->
                if (!rsp.isSuccessful) throw IOException("Download failed: HTTP ${rsp.code}")
                val append = rsp.code == 206
                var have = if (append) partial.length() else 0L
                java.io.FileOutputStream(partial, append).use { out ->
                    rsp.body!!.byteStream().use { input ->
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            have += n
                            report(step, have.toFloat() / part.size)
                        }
                    }
                }
            }
            report("Checking the download", null)
            if (partial.length() != part.size || sha256(partial) != part.sha256) {
                partial.delete()
                throw IOException("The download didn't match the pinned build. Try again.")
            }
            dest.delete()
            partial.renameTo(dest)
        }
        return dest
    }

    /** Builds [dir]'s new contents in a temporary folder, then swaps it in. */
    private fun replaceDir(dir: File, fill: (File) -> Unit) {
        val tmp = File(dir.path + ".new")
        tmp.deleteRecursively()
        tmp.mkdirs()
        fill(tmp)
        dir.deleteRecursively()
        if (!tmp.renameTo(dir)) throw IOException("Couldn't move ${dir.name} into place.")
    }

    /** Unpacks a .tar.zst into [dest], refusing paths outside it, links that leave it, and anything but files and folders. */
    internal fun unpack(archive: File, dest: File, strip: String = "") {
        val root = dest.canonicalFile
        TarArchiveInputStream(ZstdCompressorInputStream(archive.inputStream().buffered(1 shl 20))).use { tar ->
            while (true) {
                val e = tar.nextEntry ?: break
                val name = e.name.removePrefix("./").removePrefix(strip)
                if (name.isEmpty()) continue
                val out = File(root, name).canonicalFile
                if (!out.path.startsWith(root.path + File.separator)) throw IOException("Unsafe path in archive: ${e.name}")
                when {
                    e.isDirectory -> out.mkdirs()
                    e.isFile -> {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { tar.copyTo(it, 1 shl 16) }
                        if (e.mode and 0b001_000_000 != 0) out.setExecutable(true, true)
                    }
                    else -> throw IOException("Unexpected entry in archive: ${e.name}")
                }
            }
        }
    }

    private fun sha256(f: File): String? {
        if (!f.isFile) return null
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(1 shl 20)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return hex(md.digest())
    }

    private fun newPassword(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        return (1..16).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    fun gb(bytes: Long) = "%.1f GB".format(bytes / 1e9)

    // Measured from client-data v20.0's file list: compressed bytes to fetch and unpacked size.
    private const val CLIENT_ZIP_CORE = 455_152_000L
    private const val CLIENT_ZIP_ALL = 1_192_637_000L
    private const val CLIENT_DATA_CORE = 1_040_000_000L
    private const val CLIENT_DATA_ALL = 3_230_000_000L
}
