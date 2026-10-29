package app.gamenative.wow.legacy

import app.gamenative.wow.product.GameVersion
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyClientTest {

    private fun exeWithVersion(major: Int, minor: Int, patch: Int, build: Int): ByteArray =
        ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN).apply {
            position(300)
            putInt(0xFEEF04BD.toInt())
            putInt(0x00010000)
            putInt((major shl 16) or minor)
            putInt((patch shl 16) or build)
        }.array()

    @Test
    fun readsWotlkBuildFromVersionResource() {
        assertEquals(ClientBuild(3, 3, 5, 12340), LegacyClient.parseBuild(exeWithVersion(3, 3, 5, 12340)))
        assertEquals("1.12.1.5875", LegacyClient.parseBuild(exeWithVersion(1, 12, 1, 5875)).toString())
    }

    @Test
    fun noVersionResourceMeansUnknownBuild() {
        assertNull(LegacyClient.parseBuild(ByteArray(256)))
    }

    @Test
    fun newConfigForcesRealmAndRendererAndAddsDefaults() {
        val lines = LegacyClient.buildConfigWtf(null, LegacySettings(realmlist = " logon.example.com "), "1920x1080", "enGB")
        assertTrue("SET realmList \"logon.example.com\"" in lines)
        assertTrue("SET gxApi \"d3d9\"" in lines)
        assertTrue("SET gxResolution \"1920x1080\"" in lines)
        assertTrue("SET locale \"enGB\"" in lines)
        assertTrue("SET movie \"0\"" in lines)
    }

    @Test
    fun existingConfigKeepsPlayerChoicesButReplacesForcedKeys() {
        val existing = listOf("SET realmList \"old.server\"", "SET gxApi \"opengl\"", "SET movie \"1\"", "SET farclip \"777\"")
        val lines = LegacyClient.buildConfigWtf(existing, LegacySettings(realmlist = "new.server"), "1280x720", "enUS")
        assertTrue("SET realmList \"new.server\"" in lines)
        assertTrue("SET gxApi \"d3d9\"" in lines)
        assertTrue("SET movie \"1\"" in lines)
        assertTrue("SET farclip \"777\"" in lines)
        assertEquals(1, lines.count { it.startsWith("SET realmList ") })
        assertEquals(1, lines.count { it.startsWith("SET gxApi ") })
    }

    @Test
    fun openGlRendererWritesOpenGlApi() {
        val lines = LegacyClient.buildConfigWtf(null, LegacySettings(realmlist = "x", renderer = LegacyRenderer.OPENGL), "1280x720", "enUS")
        assertTrue("SET gxApi \"opengl\"" in lines)
    }

    @Test
    fun applyConfigWritesRealmlistIntoEveryLocale() {
        val root = Files.createTempDirectory("legacy").toFile()
        File(root, "Data/enUS").mkdirs()
        File(root, "Data/deDE").mkdirs()
        File(root, "Data/patches").mkdirs()
        LegacyClient.applyConfig(root, LegacySettings(realmlist = "logon.example.com"), "1280x720")
        assertEquals("set realmlist logon.example.com\n", File(root, "Data/enUS/realmlist.wtf").readText())
        assertEquals("set realmlist logon.example.com\n", File(root, "Data/deDE/realmlist.wtf").readText())
        assertTrue(!File(root, "Data/patches/realmlist.wtf").exists())
        assertTrue("SET locale \"deDE\"" in File(root, "WTF/Config.wtf").readLines())
        root.deleteRecursively()
    }

    @Test
    fun updatingRealmlistKeepsOtherLines() {
        val updated = LegacyClient.updateRealmlist("set realmlist old.server\r\nset patchlist patch.server\r\n", "new.server")
        assertEquals("set realmlist new.server\nset patchlist patch.server\n", updated)
        assertEquals("set realmlist new.server\n", LegacyClient.updateRealmlist(null, " new.server "))
    }

    @Test
    fun readsExistingRealmlist() {
        val root = Files.createTempDirectory("legacy").toFile()
        File(root, "Data/enUS").mkdirs()
        assertNull(LegacyClient.readRealmlist(root))
        File(root, "Data/enUS/realmlist.wtf").writeText("SET realmlist \"logon.example.com\"\r\nset patchlist x\r\n")
        assertEquals("logon.example.com", LegacyClient.readRealmlist(root))
        root.deleteRecursively()
    }

    @Test
    fun findsRootOneLevelDown() {
        val parent = Files.createTempDirectory("legacy").toFile()
        val client = File(parent, "WoW 3.3.5a").apply { mkdirs() }
        File(client, "Wow.exe").writeBytes(ByteArray(1))
        File(client, "Data").mkdirs()
        assertEquals(client, LegacyClient.findRoot(parent))
        parent.deleteRecursively()
    }

    @Test
    fun `trimLog keeps small logs whole and cuts the middle of big ones`() {
        val dir = Files.createTempDirectory("legacy").toFile()
        val small = File(dir, "small").apply { writeText("loaddll\nerr\n") }
        val out = File(dir, "out")
        LegacyClient.trimLog(small, out)
        assertEquals("loaddll\nerr\n", out.readText())

        val big = File(dir, "big")
        val size = (LegacyClient.LOG_HEAD + LegacyClient.LOG_TAIL + 1000).toInt()
        big.writeBytes(ByteArray(size) { if (it == 0) 'S'.code.toByte() else if (it == size - 1) 'E'.code.toByte() else 'x'.code.toByte() })
        LegacyClient.trimLog(big, out)
        val text = out.readText()
        assertTrue(text.startsWith("S"))
        assertTrue(text.endsWith("E"))
        assertTrue(text.contains("[... 1000 bytes cut ...]"))
        dir.deleteRecursively()
    }

    @Test
    fun perfOverlayOnlyAddsDxvkHudWithDxvk() {
        assertEquals("", LegacyClient.envVars(LegacySettings()))
        assertEquals(" ${LegacyClient.PERF_OVERLAY_ENV}", LegacyClient.envVars(LegacySettings(perfOverlay = true)))
        assertEquals("", LegacyClient.envVars(LegacySettings(perfOverlay = true, renderer = LegacyRenderer.OPENGL)))
    }

    @Test
    fun fasterEmulationAddsBox64FlagsOnlyWhenOn() {
        assertTrue("BOX64" !in LegacyClient.envVars(LegacySettings()))
        val env = LegacyClient.envVars(LegacySettings(fasterEmulation = true, renderer = LegacyRenderer.OPENGL))
        assertEquals(" ${LegacyClient.FASTER_EMULATION_ENV}", env)
        assertTrue("BOX64_DYNAREC_SAFEFLAGS=0" in env)
    }

    @Test
    fun bundledWowbox64MatchesTheVersionTheContainerAsksFor() {
        assertTrue(File("src/main/assets/wowbox64/wowbox64-${LegacyClient.WOWBOX64_VERSION}.tzst").isFile)
    }

    @Test
    fun handheldGraphicsReplaceOnlyTheirKeys() {
        val existing = listOf("SET farclip \"1277\"", "SET realmList \"logon.example.com\"", "SET ffxGlow \"1\"")
        val lines = LegacyClient.withValues(existing, LegacyClient.HANDHELD_GRAPHICS)
        assertTrue("SET realmList \"logon.example.com\"" in lines)
        assertTrue("SET farclip \"400\"" in lines)
        assertTrue("SET ffxGlow \"0\"" in lines)
        assertEquals(1, lines.count { it.startsWith("SET farclip ") })
    }

    @Test
    fun vanillaWritesTheRealmlistNextToWowExe() {
        val root = Files.createTempDirectory("legacy").toFile()
        File(root, "Data").mkdirs()
        File(root, "realmlist.wtf").writeText("set realmlist old.server\r\n")
        LegacyClient.applyConfig(root, LegacySettings(realmlist = "logon.example.com"), "1280x720", LegacyGame.VANILLA)
        assertEquals("set realmlist logon.example.com\n", File(root, "realmlist.wtf").readText())
        assertEquals("logon.example.com", LegacyClient.readRealmlist(root))
        root.deleteRecursively()
    }

    @Test
    fun laterClientsLeaveARootRealmlistAlone() {
        val root = Files.createTempDirectory("legacy").toFile()
        File(root, "Data/enUS").mkdirs()
        LegacyClient.applyConfig(root, LegacySettings(realmlist = "logon.example.com"), "1280x720", LegacyGame.TBC)
        assertTrue(File(root, "Data/enUS/realmlist.wtf").isFile)
        assertTrue(!File(root, "realmlist.wtf").exists())
        root.deleteRecursively()
    }

    @Test
    fun vanillaWithDxvkDropsGxApi() {
        val existing = listOf("SET gxApi \"opengl\"")
        val dxvk = LegacyClient.buildConfigWtf(existing, LegacySettings(realmlist = "x"), "1280x720", "enUS", LegacyGame.VANILLA)
        assertTrue(dxvk.none { it.startsWith("SET gxApi") })
        val gl = LegacyClient.buildConfigWtf(null, LegacySettings(realmlist = "x", renderer = LegacyRenderer.OPENGL), "1280x720", "enUS", LegacyGame.VANILLA)
        assertTrue("SET gxApi \"opengl\"" in gl)
        val tbc = LegacyClient.buildConfigWtf(null, LegacySettings(realmlist = "x"), "1280x720", "enUS", LegacyGame.TBC)
        assertTrue("SET gxApi \"d3d9\"" in tbc)
    }

    @Test
    fun findsVanillaExeWhateverItsCase() {
        val root = Files.createTempDirectory("legacy").toFile()
        File(root, "WoW.exe").writeBytes(ByteArray(1))
        File(root, "Data").mkdirs()
        assertEquals(root, LegacyClient.findRoot(root))
        assertEquals("WoW.exe", LegacyClient.exe(root).name)
        root.deleteRecursively()
    }

    @Test
    fun eachGameAcceptsOnlyItsOwnClient() {
        assertTrue(LegacyGame.VANILLA.accepts(ClientBuild(1, 12, 1, 5875)))
        assertTrue(!LegacyGame.VANILLA.accepts(ClientBuild(3, 3, 5, 12340)))
        assertTrue(LegacyGame.TBC.accepts(ClientBuild(2, 4, 3, 8606)))
        assertTrue(LegacyGame.WOTLK.accepts(null))
        assertTrue(LegacyGame.CATA.accepts(ClientBuild(4, 3, 4, 15595)))
        assertTrue(LegacyGame.MOP.accepts(ClientBuild(5, 4, 8, 18414)))
        assertTrue(!LegacyGame.MOP.accepts(ClientBuild(4, 3, 4, 15595)))
        assertTrue(LegacyGame.of(GameVersion.PRIVATE_548) == LegacyGame.MOP)
        assertTrue(LegacyGame.of(GameVersion.PRIVATE_243) == LegacyGame.TBC)
        assertTrue(!LegacyGame.TBC.hasAndroWherePatches && LegacyGame.WOTLK.hasAndroWherePatches)
    }
}
