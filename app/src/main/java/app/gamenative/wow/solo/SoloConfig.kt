package app.gamenative.wow.solo

import java.io.File

/**
 * Writes authserver.conf and worldserver.conf from the shipped `.conf.dist` files, changing only the keys the
 * phone needs: the database over the private socket, local-only network, no console, and no database updates
 * (the world database comes ready made).
 */
internal object SoloConfig {
    fun write(paths: SoloPaths, pathfinding: Boolean) {
        paths.etc.mkdirs()
        val db = { name: String -> "\".;${paths.socket.absolutePath};$DB_USER;$DB_PASSWORD;$name\"" }
        val common = mapOf(
            "LogsDir" to "\"${paths.logs.absolutePath}\"",
            "BindIP" to "\"127.0.0.1\"",
            "PidFile" to "\"\"",
            "LoginDatabaseInfo" to db("acore_auth"),
            "Updates.EnableDatabases" to "0",
            "Updates.AutoSetup" to "0",
            "MySQLExecutable" to "\"\"",
            "TempDir" to "\"${paths.tmp.absolutePath}\"",
        )
        render(File(paths.usr, "etc/authserver.conf.dist"), File(paths.etc, "authserver.conf"), common)
        render(
            File(paths.usr, "etc/worldserver.conf.dist"),
            File(paths.etc, "worldserver.conf"),
            common + mapOf(
                "DataDir" to "\"${paths.data.absolutePath}\"",
                "WorldDatabaseInfo" to db("acore_world"),
                "CharacterDatabaseInfo" to db("acore_characters"),
                "Console.Enable" to "0",
                "Ra.Enable" to "0",
                "SOAP.Enabled" to "0",
                "Metric.Enable" to "0",
                "Warden.Enabled" to "0",
                "ThreadPool" to "2",
                "MapUpdate.Threads" to "1",
                "MoveMaps.Enable" to if (pathfinding) "1" else "0",
            ),
        )
    }

    /** Copies [dist] to [out] with each `Key = value` line in [values] replaced; other lines are kept as they are. */
    internal fun render(dist: File, out: File, values: Map<String, String>) {
        out.writeText(apply(dist.readText(), values))
    }

    internal fun apply(text: String, values: Map<String, String>): String =
        text.lineSequence().joinToString("\n") { line ->
            val key = line.substringBefore('=', "").trim()
            values[key]?.takeIf { !line.trimStart().startsWith("#") }?.let { "$key = $it" } ?: line
        }

    const val DB_USER = "acore"
    const val DB_PASSWORD = "acore"
}
