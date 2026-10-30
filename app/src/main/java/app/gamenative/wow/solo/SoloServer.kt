package app.gamenative.wow.solo

import android.content.Context
import app.gamenative.BuildConfig
import app.gamenative.PluviaApp
import app.gamenative.events.AndroidEvent
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

sealed interface SoloStatus {
    data object Stopped : SoloStatus
    data class Starting(val step: String) : SoloStatus
    data object Running : SoloStatus
    data class Failed(val message: String) : SoloStatus
}

/**
 * Runs the solo server: MariaDB on a private socket, then AzerothCore's authserver and worldserver on
 * 127.0.0.1. They are Android programs started through the system linker, like Wine, and live as long as the
 * app process. The game ending stops them; so does [stop].
 */
object SoloServer {
    const val SHARED_LOG = "AndroWhere-solo.log"
    private const val LOG_LIMIT = 4L shl 20
    private const val SHARED_PER_LOG = 256L shl 10
    private const val SIGTERM = 15

    private val _status = MutableStateFlow<SoloStatus>(SoloStatus.Stopped)
    val status: StateFlow<SoloStatus> = _status.asStateFlow()

    private val lock = Mutex()
    private val running = linkedMapOf<String, Process>()
    private var listening = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Starts whatever isn't running yet and returns once the world server says it's ready. */
    suspend fun start(context: Context, onStep: (String) -> Unit = {}) = lock.withLock {
        if (_status.value == SoloStatus.Running && running.values.all { it.isAlive }) return@withLock
        val paths = SoloPaths.of(context)
        val state = SoloState.load(paths)
        try {
            withContext(Dispatchers.IO) {
                step("Starting the database", onStep)
                startDatabase(paths)
                SoloConfig.write(paths, state.pathfinding)
                step("Starting the login server", onStep)
                launch(paths, "authserver", listOf("-c", File(paths.etc, "authserver.conf").path), readyLine = null)
                step("Loading the world (about a minute)", onStep)
                launch(paths, "worldserver", listOf("-c", File(paths.etc, "worldserver.conf").path), readyLine = "ready...", timeoutSeconds = 600)
            }
            listenForGameEnd()
            _status.value = SoloStatus.Running
        } catch (e: Exception) {
            Timber.e(e, "Solo server didn't start")
            stopAll()
            _status.value = SoloStatus.Failed(e.message ?: "The server didn't start.")
            throw e
        }
    }

    suspend fun stop() = lock.withLock {
        withContext(Dispatchers.IO) { stopAll() }
        _status.value = SoloStatus.Stopped
    }

    /** Runs [sql] as the server's database user, starting the database for it if needed. */
    internal suspend fun sql(context: Context, sql: String) = lock.withLock {
        withContext(Dispatchers.IO) {
            val paths = SoloPaths.of(context)
            val ownDatabase = "mariadbd" !in running
            if (ownDatabase) startDatabase(paths)
            try {
                val p = command(paths, "mariadb", listOf("--socket=${paths.socket.path}", "-u${SoloConfig.DB_USER}", "-p${SoloConfig.DB_PASSWORD}", "--database=acore_auth", "--batch", "-e", sql))
                    .redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0) throw IOException("Database: ${out.trim().take(300)}")
                out
            } finally {
                if (ownDatabase) stopProcess("mariadbd", 60)
            }
        }
    }

    private fun step(text: String, onStep: (String) -> Unit) {
        _status.value = SoloStatus.Starting(text)
        onStep(text)
    }

    private suspend fun startDatabase(paths: SoloPaths) {
        if (running["mariadbd"]?.isAlive == true) return
        listOf(paths.run, paths.tmp, paths.logs).forEach { it.mkdirs() }
        killLeftovers(paths)
        paths.socket.delete()
        val share = File(paths.usr, "share/mariadb")
        launch(
            paths,
            "mariadbd",
            listOf(
                "--no-defaults",
                "--basedir=${paths.usr.path}",
                "--datadir=${paths.db.path}",
                "--socket=${paths.socket.path}",
                "--skip-networking",
                "--tmpdir=${paths.tmp.path}",
                "--pid-file=${File(paths.run, "mariadbd.pid").path}",
                "--lc-messages-dir=${share.path}",
                "--character-sets-dir=${File(share, "charsets").path}",
                "--innodb-use-native-aio=0",
                "--innodb-buffer-pool-size=256M",
                // Same redo log size the database was built with, so the first start doesn't rewrite it.
                "--innodb-log-file-size=512M",
                "--innodb-read-io-threads=2",
                "--innodb-write-io-threads=2",
                "--performance-schema=OFF",
                "--skip-log-bin",
                "--max-connections=40",
            ),
            readyLine = "ready for connections",
            timeoutSeconds = 180,
        )
    }

    private fun command(paths: SoloPaths, name: String, args: List<String>): ProcessBuilder {
        val bin = paths.bin(name).path
        val cmd = if (BuildConfig.MODERN_ANDROID) listOf("/system/bin/linker64", bin) + args else listOf(bin) + args
        return ProcessBuilder(cmd).directory(paths.root).apply {
            environment()["LD_LIBRARY_PATH"] = File(paths.usr, "lib").path
            environment()["HOME"] = paths.root.path
            environment()["TMPDIR"] = paths.tmp.path
        }
    }

    /** Starts [name] and, with a [readyLine], waits until it prints it; its output goes to logs/<name>.log. */
    private suspend fun launch(paths: SoloPaths, name: String, args: List<String>, readyLine: String?, timeoutSeconds: Long = 60) {
        File(paths.bin(name).path).setExecutable(true, true)
        val log = File(paths.logs, "$name.log")
        val process = command(paths, name, args).redirectErrorStream(true).start()
        running[name] = process
        File(paths.run, "$name.pid").writeText(pidOf(process)?.toString().orEmpty())
        val ready = CompletableDeferred<Unit>()
        val tail = ArrayDeque<String>()
        // An exception escaping this thread would kill the app, and a process in an error loop must not fill the
        // storage, so read errors end the thread quietly and the log stops at LOG_LIMIT.
        Thread({
            try {
                log.bufferedWriter().use { w ->
                    var written = 0L
                    process.inputStream.bufferedReader().forEachLine { line ->
                        if (written < LOG_LIMIT) {
                            w.write(line)
                            w.newLine()
                            w.flush()
                            written += line.length + 1
                            if (written >= LOG_LIMIT) w.write("[log cut at ${LOG_LIMIT shr 20} MB]\n")
                        }
                        synchronized(tail) {
                            tail.addLast(line)
                            if (tail.size > 8) tail.removeFirst()
                        }
                        if (readyLine != null && line.contains(readyLine)) ready.complete(Unit)
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Stopped reading $name output")
            } finally {
                ready.completeExceptionally(IOException("$name stopped: ${synchronized(tail) { tail.lastOrNull() } ?: "no output"}"))
            }
        }, "solo-$name").apply { isDaemon = true }.start()
        if (readyLine == null) {
            if (process.waitFor(2, TimeUnit.SECONDS)) throw IOException("$name stopped right away. See ${log.name}.")
            return
        }
        val ok = withTimeoutOrNull(timeoutSeconds * 1000) { ready.await() }
        if (ok == null) throw IOException("$name took too long to start. See ${log.name}.")
    }

    private fun stopAll() {
        stopProcess("worldserver", 60)
        stopProcess("authserver", 10)
        stopProcess("mariadbd", 60)
    }

    /** Asks [name] to shut down cleanly with SIGTERM. Process.destroy() would also close its output under the reader thread. */
    private fun stopProcess(name: String, seconds: Long) {
        val p = running.remove(name) ?: return
        val pid = pidOf(p)
        if (pid != null) android.os.Process.sendSignal(pid, SIGTERM) else p.destroy()
        if (!p.waitFor(seconds, TimeUnit.SECONDS)) p.destroyForcibly()
    }

    /** Joins the last part of each server log into one file the share sheet can send, or null if there are none. */
    fun shareableLog(context: Context): File? {
        val logs = SoloPaths.of(context).logs.listFiles { f -> f.extension == "log" }?.sortedBy { it.name }.orEmpty()
        if (logs.isEmpty()) return null
        val out = File(context.externalCacheDir, "solo_logs/$SHARED_LOG").apply { parentFile?.mkdirs() }
        out.bufferedWriter().use { w ->
            logs.forEach { f ->
                w.write("===== ${f.name} =====\n")
                f.inputStream().use { input ->
                    input.skip((f.length() - SHARED_PER_LOG).coerceAtLeast(0))
                    w.write(input.readBytes().decodeToString())
                }
                w.write("\n")
            }
        }
        return out
    }

    /** Ends server processes left behind by an app that was killed while they ran. */
    private fun killLeftovers(paths: SoloPaths) {
        listOf("worldserver", "authserver", "mariadbd").forEach { name ->
            if (running[name]?.isAlive == true) return@forEach
            val pidFile = File(paths.run, "$name.pid")
            val pid = pidFile.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull() ?: return@forEach
            val cmdline = runCatching { File("/proc/$pid/cmdline").readText() }.getOrNull()
            if (cmdline != null && cmdline.contains(paths.bin(name).path)) {
                android.os.Process.sendSignal(pid, android.os.Process.SIGNAL_KILL)
                Thread.sleep(500)
            }
            pidFile.delete()
        }
    }

    private fun pidOf(p: Process): Int? = runCatching {
        p.javaClass.getDeclaredField("pid").apply { isAccessible = true }.getInt(p)
    }.getOrNull()

    private fun listenForGameEnd() {
        if (listening) return
        listening = true
        val end: (Any) -> Unit = { scope.launch { stop() } }
        PluviaApp.events.on<AndroidEvent.GuestProgramTerminated, Unit>(end)
        PluviaApp.events.on<AndroidEvent.ForceCloseApp, Unit>(end)
    }
}
