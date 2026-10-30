package app.gamenative.wow.legacy.mods

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientModsTest {

    private fun tempDir(): File = Files.createTempDirectory("clientmods").toFile()

    @Test
    fun archiveRoundTrips() {
        val archive = File(tempDir(), "patch-enUS-Z.MPQ")
        val toc = "GlueStrings.lua\r\nAccountLogin.xml\r\n".toByteArray()
        val lua = ByteArray(5000) { (it % 251).toByte() }
        Mpq.write(archive, mapOf("Interface\\GlueXML\\GlueXML.toc" to toc, "Interface\\GlueXML\\AndroWhereLogin.lua" to lua))

        assertArrayEquals(toc, Mpq.read(archive, "Interface\\GlueXML\\GlueXML.toc"))
        assertArrayEquals(lua, Mpq.read(archive, "interface/gluexml/androwherelogin.lua"))
        assertNull(Mpq.read(archive, "Interface\\GlueXML\\Missing.lua"))
    }

    @Test
    fun loginFileGoesBeforeTheLastStockFile() {
        val toc = "GlueStrings.lua\r\nServerList.xml\r\n## This Always Runs Last, Add New Items above it.\r\nGlueLocalizationPost.xml\r\n"
        val lines = ClientMods.withLoginFile(toc).split("\r\n")
        assertEquals(listOf("GlueStrings.lua", "ServerList.xml", "AndroWhereLogin.lua", "## This Always Runs Last, Add New Items above it."), lines.take(4))
        assertEquals(1, ClientMods.withLoginFile(ClientMods.withLoginFile(toc)).split("\r\n").count { it == "AndroWhereLogin.lua" })
    }

    @Test
    fun letteredPatchesOutrankNumberedOnes() {
        assertTrue(ClientMods.priority("patch-enUS-c.mpq") > ClientMods.priority("patch-enUS-9.MPQ"))
        assertTrue(ClientMods.priority("patch-enUS-Z.MPQ") > ClientMods.priority("patch-enUS-c.mpq"))
        assertTrue(ClientMods.priority("patch-enUS-2.MPQ") > ClientMods.priority("patch-enUS.MPQ"))
    }

    @Test
    fun patchesGroupByLetterAndSkipBlizzardFiles() {
        val root = tempDir()
        val data = File(root, "Data").apply { mkdirs() }
        val locale = File(data, "enUS").apply { mkdirs() }
        listOf("patch.MPQ", "patch-2.MPQ", "patch-3.MPQ", "common.MPQ", "patch-a.mpq", "patch-j.mpq.disabled", "patch-4.mpq").forEach { File(data, it).writeBytes(ByteArray(10)) }
        listOf("patch-enUS.MPQ", "patch-enUS-2.MPQ", "patch-enUS-a.mpq", "locale-enUS.MPQ").forEach { File(locale, it).writeBytes(ByteArray(10)) }
        File(data, "Patch_Lables.txt").writeText("patch-4.MPQ - Music\nPatch-A.MPQ - HD Characters\n")

        val patches = ClientMods.patches(root)
        assertEquals(listOf("4", "A", "J"), patches.map { it.key })
        val hd = patches.first { it.key == "A" }
        assertEquals("HD Characters", hd.name)
        assertEquals(2, hd.files.size)
        assertEquals(20L, hd.size)
        assertTrue(hd.enabled)
        assertFalse(patches.first { it.key == "J" }.enabled)
        assertEquals("Patch J", patches.first { it.key == "J" }.name)
    }

    @Test
    fun turningAPatchOffAndOnRenamesBothFiles() {
        val root = tempDir()
        val data = File(root, "Data/enUS").apply { mkdirs() }.parentFile
        File(data, "patch-a.mpq").writeBytes(ByteArray(1))
        File(data, "enUS/patch-enUS-a.mpq").writeBytes(ByteArray(1))

        ClientMods.setPatchEnabled(ClientMods.patches(root).single(), false)
        assertTrue(File(data, "patch-a.mpq.disabled").isFile)
        assertTrue(File(data, "enUS/patch-enUS-a.mpq.disabled").isFile)
        ClientMods.setPatchEnabled(ClientMods.patches(root).single(), true)
        assertTrue(File(data, "patch-a.mpq").isFile)
        assertTrue(File(data, "enUS/patch-enUS-a.mpq").isFile)
    }

    @Test
    fun readsNamesFromAnHdPackMenuConfig() {
        val root = tempDir()
        File(root, "patchmenu/.config").mkdirs()
        File(root, "patchmenu/.config/config_en.json").writeText(
            """{"patches":{"dynamic":{"a":{"name":"(9)HD characters","conditions":[[]]},"6":{"name":"(9)HD textures of the world"}}}}""",
        )
        assertEquals(mapOf("A" to "HD characters", "6" to "HD textures of the world"), ClientMods.labels(root))
    }

    @Test
    fun serverAndPasswordFilesGoBeforeTheLoginFile() {
        val toc = "GlueStrings.lua\r\n## This Always Runs Last\r\n"
        val lines = ClientMods.withLoginFile(ClientMods.withLoginFile(toc, true, true), true, true).split("\r\n")
        assertEquals(
            listOf("GlueStrings.lua", "AndroWhereServers.lua", "AndroWhereLoginData.lua", "AndroWhereLogin.lua", "## This Always Runs Last"),
            lines.take(5),
        )
        assertFalse(ClientMods.withLoginFile(ClientMods.withLoginFile(toc, true, true)).contains("AndroWhereLoginData.lua"))
    }

    @Test
    fun luaStringsAreByteEscapesOnly() {
        assertEquals("\"\\97\\34\\92\\93\\195\\169\"", ClientMods.luaString("a\"\\]é"))
    }

    private val twoServers = LoginData(
        servers = listOf(GlueServer("Warmane", "logon.warmane.com", "me"), GlueServer("Other", "logon.other.org", "")),
        selected = 1,
        passwords = listOf("pw", null),
        autoLogin = true,
    )

    @Test
    fun serversAndPasswordsBecomeLuaTables() {
        val servers = ClientMods.serversLua(twoServers).toString(Charsets.ISO_8859_1)
        assertTrue(servers.startsWith("AndroWhereServers = {\r\n    { name = ${ClientMods.luaString("Warmane")}, realmlist = "))
        assertTrue(servers.endsWith("}\r\nAndroWhereServerIndex = 2\r\n"))
        assertEquals(
            "AndroWhereLoginPasswords = {\r\n    [1] = ${ClientMods.luaString("pw")},\r\n}\r\nAndroWhereLoginAuto = true\r\n",
            ClientMods.passwordsLua(twoServers)!!.toString(Charsets.ISO_8859_1),
        )
        assertNull(ClientMods.passwordsLua(twoServers.copy(passwords = listOf(null, null))))
        assertNull(ClientMods.passwordsLua(twoServers.copy(passwords = null)))
    }

    @Test
    fun wipingThePasswordsKeepsTheServersAndTheLoginAddOn() {
        val root = tempDir()
        exeWithSpots(root)
        val locale = File(root, "Data/enUS").apply { mkdirs() }
        Mpq.write(File(locale, "locale-enUS.MPQ"), mapOf("Interface\\GlueXML\\GlueXML.toc" to "GlueStrings.lua\r\n".toByteArray()))
        fun toc() = Mpq.read(ClientMods.archive(root), "Interface\\GlueXML\\GlueXML.toc")!!.toString(Charsets.ISO_8859_1)

        ClientMods.enableLogin(root, twoServers)
        assertTrue(ClientMods.hasPassword(root))
        assertTrue(toc().contains("AndroWhereLoginData.lua"))

        ClientMods.wipePassword(root)
        assertFalse(ClientMods.hasPassword(root))
        assertTrue(ClientMods.loginEnabled(root))
        assertFalse(toc().contains("AndroWhereLoginData.lua"))
        assertTrue(toc().contains("AndroWhereServers.lua"))
        assertTrue(Mpq.read(ClientMods.archive(root), "Interface\\GlueXML\\AndroWhereLogin.lua") != null)
        assertEquals(listOf("locale-enUS.MPQ", ClientMods.archive(root).name), locale.list()!!.sorted())

        ClientMods.refresh(root)
        assertTrue(toc().contains("AndroWhereServers.lua"))
    }

    private fun exeWithSpots(dir: File = tempDir()): File {
        val exe = File(dir, "Wow.exe")
        RandomAccessFile(exe, "rw").use { file ->
            file.setLength(0x420000)
            mapOf(0x415A25L to "75", 0x415A3FL to "01", 0x415A95L to "01", 0x415B46L to "7F", 0x415B5FL to "83C0035E8BE55D").forEach { (offset, hex) ->
                file.seek(offset)
                file.write(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            }
        }
        return exe
    }

    @Test
    fun exePatchKeepsAndRestoresTheOriginal() {
        val exe = exeWithSpots()
        val original = exe.readBytes()
        assertEquals(WowExePatch.State.ORIGINAL, WowExePatch.state(exe))

        WowExePatch.apply(exe)
        assertEquals(WowExePatch.State.PATCHED, WowExePatch.state(exe))
        assertTrue(WowExePatch.backupOf(exe).isFile)

        WowExePatch.restore(exe)
        assertArrayEquals(original, exe.readBytes())
        assertFalse(WowExePatch.backupOf(exe).isFile)
    }

    @Test
    fun anExeAlreadyPatchedElsewhereIsLeftAlone() {
        val exe = exeWithSpots()
        WowExePatch.apply(exe)
        WowExePatch.backupOf(exe).delete()
        val patched = exe.readBytes()

        WowExePatch.apply(exe)
        WowExePatch.restore(exe)
        assertArrayEquals(patched, exe.readBytes())
    }

    @Test(expected = IllegalStateException::class)
    fun anUnknownExeIsRefused() {
        val exe = File(tempDir(), "Wow.exe").apply { writeBytes(ByteArray(0x420000)) }
        WowExePatch.apply(exe)
    }

    @Test
    fun levelUpBannerInstallsAsAPlainAddonAndRemovesCleanly() {
        val root = tempDir()
        assertFalse(ClientMods.installed(root, EverywherePatch.LEVEL_UP))

        ClientMods.setInstalled(root, EverywherePatch.LEVEL_UP, true)
        val dir = File(root, "Interface/AddOns/${LevelUpBanner.FOLDER}")
        assertTrue(ClientMods.installed(root, EverywherePatch.LEVEL_UP))
        assertTrue(File(dir, "${LevelUpBanner.FOLDER}.toc").readText().startsWith("## Interface: 30300"))
        assertTrue(File(dir, "LevelUp.lua").readText().contains("PLAYER_LEVEL_UP"))
        assertFalse(File(root, "Data").exists())

        ClientMods.setInstalled(root, EverywherePatch.LEVEL_UP, false)
        assertFalse(dir.exists())
        assertTrue(File(root, "Interface/AddOns").isDirectory)
    }
}
