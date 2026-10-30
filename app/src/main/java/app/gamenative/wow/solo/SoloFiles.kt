package app.gamenative.wow.solo

import android.content.Context
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Where the solo server lives, all inside the app's private storage so no other app can reach it. */
class SoloPaths(val root: File) {
    val usr = File(root, "usr")
    val db = File(root, "db")
    val data = File(root, "data")
    val etc = File(root, "etc")
    val run = File(root, "run")
    val tmp = File(run, "tmp")
    val logs = File(root, "logs")
    val downloads = File(root, "downloads")
    val socket = File(run, "mysql.sock")
    val stateFile = File(root, "state.json")

    fun bin(name: String) = File(usr, "bin/$name")

    companion object {
        fun of(context: Context) = SoloPaths(File(context.filesDir, "solo"))
    }
}

/** One file of the solo server release, pinned by size and sha256. */
@Serializable
data class SoloPart(val url: String, val size: Long, val sha256: String)

@Serializable
data class ClientDataSource(val url: String, val sha256: String)

/** `assets/solo/solo-server.json`, written by the solo-server workflow: the only builds the app accepts. */
@Serializable
data class SoloManifest(
    val azerothcore: String,
    val mariadb: String,
    val server: SoloPart,
    val serverUnpacked: Long,
    val database: SoloPart,
    val databaseUnpacked: Long,
    val clientDataHashes: SoloPart,
    val clientData: ClientDataSource,
    val build: String = "",
) {
    /** Names the server build; changes whenever the server files do, even for the same AzerothCore commit. */
    val serverBuild get() = build.ifEmpty { azerothcore }

    companion object {
        const val ASSET = "solo/solo-server.json"

        fun load(context: Context): SoloManifest? =
            runCatching { context.assets.open(ASSET).use { json.decodeFromString<SoloManifest>(it.readBytes().decodeToString()) } }.getOrNull()
    }
}

/** What setup has finished, so an interrupted setup picks up where it stopped. */
@Serializable
data class SoloState(
    val server: String = "",
    val database: String = "",
    val dataFolders: Set<String> = emptySet(),
    val account: String = "",
    val gm: Boolean = true,
    val serverId: String = "",
) {
    val pathfinding get() = ClientData.PATHING in dataFolders

    fun isReady(manifest: SoloManifest, wantPathing: Boolean) =
        server == manifest.serverBuild && database.isNotEmpty() && dataFolders.containsAll(ClientData.folders(wantPathing)) && account.isNotEmpty()

    companion object {
        fun load(paths: SoloPaths): SoloState =
            runCatching { json.decodeFromString<SoloState>(paths.stateFile.readText()) }.getOrDefault(SoloState())

        fun save(paths: SoloPaths, state: SoloState) {
            paths.root.mkdirs()
            val tmp = File(paths.root, "state.json.tmp")
            tmp.writeText(json.encodeToString(state))
            tmp.renameTo(paths.stateFile)
        }
    }
}

private val json = Json { ignoreUnknownKeys = true }
