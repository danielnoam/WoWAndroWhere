package app.gamenative.wow.solo

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoloTest {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @Test
    fun verifierMatchesAzerothCore() {
        // Same as TrinityCore's PHP registration snippet (gmp, least significant byte first) for SOLO:SECRET.
        val salt = ByteArray(32) { it.toByte() }
        assertEquals("799d9ee457aa12c4891da8ac5e367f82411bf43946292b29c48e1fb372ad7e51", hex(Srp6.verifier("solo", "secret", salt)))
        assertEquals(32, Srp6.verifier("a", "b", Srp6.newSalt()).size)
    }

    @Test
    fun configReplacesOnlyTheGivenKeys() {
        val dist = "# BindIP = \"x\"\nBindIP = \"0.0.0.0\"\nDataDir = \".\"\nLogger.root=2,Console Server\n"
        val out = SoloConfig.apply(dist, mapOf("BindIP" to "\"127.0.0.1\"", "Missing" to "1"))
        assertEquals("# BindIP = \"x\"\nBindIP = \"127.0.0.1\"\nDataDir = \".\"\nLogger.root=2,Console Server\n", out)
    }

    @Test
    fun readsZipCentralDirectory() {
        val bytes = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { z ->
                z.putNextEntry(ZipEntry("dbc/Spell.dbc")); z.write(ByteArray(1000) { 7 }); z.closeEntry()
                z.putNextEntry(ZipEntry("maps/0000000.map")); z.write("map".toByteArray()); z.closeEntry()
            }
        }.toByteArray()
        val eocd = (bytes.size - 22 downTo 0).first { bytes[it] == 0x50.toByte() && bytes[it + 1] == 0x4b.toByte() && bytes[it + 2] == 5.toByte() && bytes[it + 3] == 6.toByte() }
        fun le32(at: Int) = (0..3).sumOf { (bytes[at + it].toLong() and 0xFF) shl (8 * it) }
        val cdSize = le32(eocd + 12).toInt()
        val cdOffset = le32(eocd + 16).toInt()
        val entries = ClientData.parseCentralDirectory(bytes.copyOfRange(cdOffset, cdOffset + cdSize), 2)
        assertEquals(listOf("dbc/Spell.dbc", "maps/0000000.map"), entries.map { it.name })
        assertEquals(listOf("dbc", "maps"), entries.map { it.folder })
        assertEquals(1000L, entries[0].size)
        assertEquals(0L, entries[0].offset)
    }

    @Test(expected = IOException::class)
    fun refusesPathsOutsideTheDataFolder() {
        ClientData.safeChild(File("/tmp/x"), "dbc/../../etc/passwd")
    }

    @Test
    fun pathingIsOptional() {
        assertFalse(ClientData.PATHING in ClientData.folders(false))
        assertTrue(ClientData.folders(true).containsAll(ClientData.folders(false) + ClientData.PATHING))
    }

    private fun tarZst(dest: File, vararg files: Pair<String, Int>) {
        TarArchiveOutputStream(ZstdCompressorOutputStream(dest.outputStream())).use { tar ->
            files.forEach { (name, mode) ->
                val data = name.toByteArray()
                tar.putArchiveEntry(TarArchiveEntry(name).apply { size = data.size.toLong(); this.mode = mode })
                tar.write(data)
                tar.closeArchiveEntry()
            }
        }
    }

    @Test
    fun unpacksAndStripsTheTopFolder() {
        val dir = Files.createTempDirectory("solo").toFile()
        val archive = File(dir, "a.tar.zst")
        tarZst(archive, "usr/bin/worldserver" to "755".toInt(8), "usr/etc/worldserver.conf.dist" to "644".toInt(8))
        val out = File(dir, "out").apply { mkdirs() }
        SoloSetup.unpack(archive, out, strip = "usr/")
        assertTrue(File(out, "bin/worldserver").canExecute())
        assertEquals("usr/etc/worldserver.conf.dist", File(out, "etc/worldserver.conf.dist").readText())
        dir.deleteRecursively()
    }

    @Test(expected = IOException::class)
    fun refusesArchivePathsOutsideTheFolder() {
        val dir = Files.createTempDirectory("solo").toFile()
        val archive = File(dir, "a.tar.zst")
        tarZst(archive, "usr/../../evil" to "644".toInt(8))
        SoloSetup.unpack(archive, File(dir, "out").apply { mkdirs() }, strip = "usr/")
    }

    @Test
    fun accountSqlCreatesTheAccountAndGm() {
        val sql = SoloSetup.accountSql("SOLO", "PASSWORD", gm = true)
        assertTrue(sql.startsWith("INSERT INTO acore_auth.account (username, salt, verifier, expansion) VALUES ('SOLO', X'"))
        assertTrue(sql.contains("INSERT INTO acore_auth.account_access (id, gmlevel, RealmID) SELECT id, 3, -1"))
        assertFalse(SoloSetup.gmSql("SOLO", on = false).contains("INSERT"))
        assertFalse(sql.contains("DELETE a "))
    }
}
