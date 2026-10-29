package app.gamenative.wow.ui

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.gamenative.PluviaApp
import app.gamenative.PrefManager
import app.gamenative.events.AndroidEvent
import app.gamenative.ui.screen.wow.BattleNetSignIn
import app.gamenative.ui.screen.wow.GamePath
import app.gamenative.ui.screen.wow.GpuProfile
import app.gamenative.ui.screen.wow.StorageAccessDeniedException
import app.gamenative.ui.screen.wow.WoWLauncherState
import app.gamenative.ui.screen.wow.WowClientDownloader
import app.gamenative.ui.screen.wow.fitScreenSizeToWindow
import app.gamenative.ui.screen.wow.installLocale
import app.gamenative.wow.addons.CurseForgeAccess
import app.gamenative.wow.install.GameInstaller
import app.gamenative.wow.install.InstallController
import app.gamenative.wow.install.InstallProgress
import app.gamenative.wow.install.InstallRequest
import app.gamenative.wow.install.InstallUiState
import app.gamenative.ui.screen.wow.isPermissionDenied
import app.gamenative.ui.screen.wow.prepareLaunch
import app.gamenative.ui.screen.wow.prepareLegacyLaunch
import app.gamenative.utils.AppUpdater
import app.gamenative.utils.StorageUtils
import app.gamenative.wow.legacy.ClientBuild
import app.gamenative.wow.legacy.LegacyClient
import app.gamenative.wow.legacy.LegacyServer
import app.gamenative.wow.legacy.LegacyServers
import app.gamenative.wow.legacy.LegacySettings
import app.gamenative.wow.legacy.mods.FastLogin
import app.gamenative.wow.product.GameInstall
import app.gamenative.wow.product.GameVersion
import app.gamenative.wow.product.GameVersions
import app.gamenative.wow.product.InstallRegistry
import app.gamenative.wow.product.ProductConverter
import app.gamenative.wow.product.WowProduct
import app.gamenative.wow.addons.InstalledAddon
import app.gamenative.wow.profile.Profile
import app.gamenative.wow.ui.addons.AddonProfile
import app.gamenative.wow.profile.ProfileAddons
import app.gamenative.wow.profile.Profiles
import app.gamenative.wow.solo.SoloServer
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Launch, game-update and app-update state for the active install. The logic is moved from upstream's
 * `WoWForeverScreen` with its behavior kept: auto-launch on start when everything is ready, an outdated
 * game blocks Play with "Update" / "Launch anyway", and login.txt is wiped on resume and force-close.
 */
class LauncherController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onLaunch: (appId: String) -> Unit,
) {
    val registry = InstallRegistry(context)
    internal val gpu by lazy { GpuProfile.detect(context) }

    /** The saved profiles and the active one, whose version and folder the whole app works with. */
    var profiles by mutableStateOf(Profiles.all)
        private set
    var profile by mutableStateOf(Profiles.active)
        private set

    var version by mutableStateOf(profile?.gameVersion ?: GameVersions.selected)
        private set

    var gamePath by mutableStateOf(profile?.root.orEmpty())
        private set
    var files by mutableStateOf(GamePath.Status())
        private set
    var hasSavedLogin by mutableStateOf(BattleNetSignIn.load(context) != null)
        private set

    var isLaunching by mutableStateOf(false)
        private set
    var statusText by mutableStateOf(READY)
        private set
    var errorMessage by mutableStateOf<String?>(null)
    var needsStoragePermission by mutableStateOf(false)

    var versionStatus by mutableStateOf<WowClientDownloader.VersionCheckResult?>(null)
        private set
    var isCheckingVersion by mutableStateOf(false)
        private set

    /** False until the first version check has finished (successfully or not). */
    var versionChecked by mutableStateOf(false)
        private set
    var isUpdating by mutableStateOf(false)
        private set
    var updateStatusText by mutableStateOf("")
        private set

    var appUpdate by mutableStateOf<AppUpdater.Release?>(null)
        private set
    var isDownloadingAppUpdate by mutableStateOf(false)
        private set
    var appUpdateProgress by mutableFloatStateOf(0f)
        private set
    var downloadedApk by mutableStateOf<File?>(null)
        private set
    var needsInstallPermission by mutableStateOf(false)

    /**
     * Set when Play is pressed on a device without an Adreno GPU (an emulator, a non-Snapdragon phone).
     * The game's graphics stack crashes the whole app there, so ask before launching.
     */
    var unsupportedDeviceWarning by mutableStateOf(false)
    private val supportedGpu = runCatching { com.winlator.core.GPUInformation.isAdrenoGPU(context) }.getOrDefault(true)

    /** Launch-version product is live and this install only has the beta. */
    var canConvertToLaunch by mutableStateOf(false)
        private set

    var legacy by mutableStateOf(LegacyClient.load(context))

    /** Set after Use lighter graphics, so the screen can confirm it. */
    var legacyGraphicsApplied by mutableStateOf(false)
        private set
    var legacyBuild by mutableStateOf<ClientBuild?>(null)
        private set

    var hasServerPassword by mutableStateOf(LegacyClient.hasServerPassword(context))
        private set

    /** The private servers saved in the app; the selected one is [legacy]'s serverId. */
    var servers by mutableStateOf(LegacyServers.load(context, legacy))
        private set

    val selectedServer get() = servers.firstOrNull { it.id == legacy.serverId } ?: servers.first()

    /** The player's own CurseForge key or proxy from Settings; CurseForge is off without one. */
    var curseForge by mutableStateOf(CurseForgeAccess.load(context))
        private set

    /** The Wine log of the last legacy launch, waiting for the player to agree to share it. */
    var legacyLogToShare by mutableStateOf<File?>(null)

    private var launchJob: Job? = null
    private var pendingInstallPermission = false

    /** The private server version runs the player's own client from [legacy]: no Blizzard install, updates or login. */
    val isPrivate get() = version.isPrivateServer
    val filesMissing get() = if (isPrivate) legacy.root.isBlank() else !(files.dataExists && files.buildInfoExists)
    val canPlay get() = !isLaunching && !isUpdating && !filesMissing && if (isPrivate) legacy.realmlist.isNotBlank() else !files.isWrongGame
    val busy get() = isLaunching || isUpdating

    fun checkFiles(): Boolean {
        if (isPrivate || gamePath.isBlank()) {
            files = GamePath.Status()
            return !filesMissing
        }
        files = GamePath.status(context, gamePath)
        if (files.isWrongGame) errorMessage = "This folder holds ${files.productCode}, not ${version.displayName}."
        return files.isReady
    }

    /** True while a game download is running or paused; switching profiles waits for it. */
    val installInProgress get() = InstallController.state.value.let { it is InstallUiState.Running || it is InstallUiState.Paused }

    /** Why profiles can't be switched right now, or null when they can. */
    val switchBlocked: String?
        get() = when {
            installInProgress -> "Finish or cancel the download before switching profiles."
            busy -> "Wait for the game to start or the update to finish before switching profiles."
            else -> null
        }

    /** Switches the whole app to the profile [id]. Refused (false) while a download, update or launch is under way. */
    fun switchProfile(id: String): Boolean {
        if (id == profile?.id) return true
        if (switchBlocked != null) return false
        FastLogin.wipe(context)
        Profiles.activate(context, id)
        loadProfile()
        return true
    }

    /** The profile before or after the active one, for L1 and R1 on the home screen. */
    fun cycleProfile(step: Int) {
        val list = profiles
        if (list.size < 2) return
        val index = list.indexOfFirst { it.id == profile?.id }
        switchProfile(list[Math.floorMod(index + step, list.size)].id)
    }

    /** Makes and switches to a new profile. A Blizzard profile left without a folder is replaced by it. */
    fun createProfile(name: String, version: GameVersion): Boolean {
        if (switchBlocked != null) return false
        val unset = profile?.takeIf { it.root.isBlank() && !it.gameVersion.isPrivateServer }
        val created = Profiles.create(context, name, version, replacing = unset?.id)
        FastLogin.wipe(context)
        Profiles.activate(context, created.id)
        unset?.let { Profiles.remove(context, it.id) }
        loadProfile()
        return true
    }

    fun renameProfile(id: String, name: String) {
        val target = profiles.firstOrNull { it.id == id } ?: return
        Profiles.update(context, target.copy(name = Profiles.freeName(name.trim().ifBlank { target.gameVersion.displayName }, except = id)))
        profiles = Profiles.all
        profile = Profiles.active
    }

    fun deleteProfile(id: String) {
        Profiles.remove(context, id)
        profiles = Profiles.all
    }

    private fun loadProfile() {
        profiles = Profiles.all
        profile = Profiles.active
        version = profile?.gameVersion ?: GameVersions.selected
        errorMessage = null
        versionStatus = null
        versionChecked = false
        canConvertToLaunch = false
        gamePath = profile?.root.orEmpty()
        legacy = LegacyClient.load(context)
        servers = LegacyServers.load(context, legacy)
        hasServerPassword = LegacyClient.hasServerPassword(context)
        legacyBuild = null
        legacyGraphicsApplied = false
        if (checkFiles()) checkVersionStatus()
        if (isPrivate) {
            detectLegacyBuild()
            applyClientMods()
        }
    }

    /** Sets the active profile's folder, and splits addons and client patches when another profile already uses it. */
    private fun setProfileRoot(root: String) {
        val current = profile ?: return
        if (current.root == root) return
        val updated = current.copy(root = root)
        Profiles.update(context, updated)
        profiles = Profiles.all
        profile = updated
        val addonsDir = addonsDir(updated) ?: return
        val clientRoot = File(root).takeIf { updated.gameVersion.isPrivateServer }
        scope.launch(Dispatchers.IO) {
            runCatching { ProfileAddons.joined(context, updated, addonsDir, clientRoot) }.onFailure { Timber.w(it, "Sharing a folder") }
        }
    }

    /** Called before a fresh download into [root], so the profile follows it even if the download finishes in the background. */
    fun startedInstall(root: String) {
        setProfileRoot(root)
        gamePath = root
    }

    private fun addonsDir(p: Profile): File? = when {
        p.root.isBlank() -> null
        p.gameVersion.isPrivateServer -> File(p.root, "Interface/AddOns")
        else -> (files.product ?: p.gameVersion.products.firstOrNull())?.let { File(p.root, "${it.flavorDir}/Interface/AddOns") }
    }

    /** The addon screen's view of the active profile's folder. */
    fun addonProfile(): AddonProfile {
        val current = profile ?: return AddonProfile.ALL
        return object : AddonProfile {
            override fun visibility() = ProfileAddons.visibility(context, current)
            override fun claim(ids: Set<String>) = ProfileAddons.claim(context, current, ids)
            override fun release(addon: InstalledAddon) = ProfileAddons.release(context, current, addon)
            override fun modsChanged() = recordClientMods()
        }
    }

    /** Saves the 3.3.5a client patches the player set for this profile. */
    fun recordClientMods() {
        val current = profile ?: return
        val root = legacy.root.takeIf { it.isNotBlank() } ?: return
        scope.launch(Dispatchers.IO) { runCatching { ProfileAddons.recordMods(context, current, File(root)) } }
    }

    private fun applyClientMods() {
        val current = profile ?: return
        val root = legacy.root.takeIf { it.isNotBlank() } ?: return
        scope.launch(Dispatchers.IO) {
            runCatching { ProfileAddons.applyMods(context, current, File(root)) }.onFailure { Timber.w(it, "Applying client patches") }
        }
    }

    /** Turns off the addons this profile doesn't use, when its folder is shared. Runs as part of the launch. */
    private fun applyProfileAddons(wtfDir: File) {
        val current = profile ?: return
        val dir = addonsDir(current) ?: return
        runCatching { ProfileAddons.applyAtLaunch(context, current, dir, wtfDir) }.onFailure { Timber.w(it, "Writing AddOns.txt") }
    }

    fun refresh() {
        checkFiles()
        checkVersionStatus()
        checkAppUpdate()
    }

    fun selectInstall(install: GameInstall) {
        registry.add(install)
        registry.setActive(install.root)
        useGameRoot(install.root)
    }

    /** Points the launcher at a game root picked by the user (folder picker or scan). */
    fun useGameRoot(root: String) {
        errorMessage = null
        gamePath = root
        GamePath.save(context, root)
        versionStatus = null
        val ok = checkFiles()
        setProfileRoot(root)
        files.product?.let { registry.add(GameInstall(root, it, versionStatus?.localVersion.orEmpty())) }
        registry.setActive(root)
        if (!files.hasStorageAccess) needsStoragePermission = true else if (ok) checkVersionStatus()
    }

    fun onLoginSaved(login: BattleNetSignIn.Login) {
        if (BattleNetSignIn.save(context, login)) {
            hasSavedLogin = true
        } else {
            errorMessage = "Couldn't save your login on this device. You can still sign in inside the game."
        }
    }

    fun forgetLogin() {
        BattleNetSignIn.forget(context)
        if (gamePath.isNotBlank()) BattleNetSignIn.removeLoginFile(File(gamePath))
        hasSavedLogin = false
    }

    private suspend fun fetchVersionStatus() {
        isCheckingVersion = true
        val product = files.product
        versionStatus = withContext(Dispatchers.IO) { WowClientDownloader.checkVersion(File(gamePath), product) }
        canConvertToLaunch = product == WowProduct.BETA &&
            withContext(Dispatchers.IO) { runCatching { WowClientDownloader.isProductLive(WowProduct.FOREVER) }.getOrDefault(false) }
        isCheckingVersion = false
        versionChecked = true
    }

    fun checkVersionStatus() {
        if (isPrivate || !File(gamePath, WowClientDownloader.BUILD_INFO).exists()) return
        scope.launch { fetchVersionStatus() }
    }

    private suspend fun fetchAppUpdate(): AppUpdater.Release? = AppUpdater.check().also { appUpdate = it }

    fun checkAppUpdate() {
        scope.launch { fetchAppUpdate() }
    }

    fun launchGame(confirmedUnsupported: Boolean = false) {
        if (isPrivate) return launchLegacy(confirmedUnsupported)
        val product = files.product
        if (isLaunching || product == null) return
        if (!supportedGpu && !confirmedUnsupported) {
            unsupportedDeviceWarning = true
            return
        }
        if (!files.hasStorageAccess) {
            needsStoragePermission = true
            return
        }
        launch { screenSize, onStatus ->
            applyProfileAddons(File(gamePath, "${product.flavorDir}/WTF"))
            prepareLaunch(context, File(gamePath), product, gpu, screenSize, onStatus)
        }
    }

    fun saveServerPassword(password: String) {
        hasServerPassword = LegacyClient.saveServerPassword(context, password)
        if (!hasServerPassword) errorMessage = "Couldn't save the password on this device."
    }

    fun forgetServerPassword() {
        LegacyClient.forgetServerPassword(context)
        hasServerPassword = false
        FastLogin.wipe(context)
    }

    fun selectServer(id: String) {
        val server = servers.firstOrNull { it.id == id } ?: return
        updateLegacy(legacy.copy(serverId = id, realmlist = server.realmlist))
        hasServerPassword = LegacyClient.hasServerPassword(context)
    }

    fun updateServer(server: LegacyServer) {
        servers = servers.map { if (it.id == server.id) server else it }
        LegacyServers.save(context, servers)
        if (server.id == legacy.serverId) updateLegacy(legacy.copy(realmlist = server.realmlist))
    }

    fun addServer() {
        val server = LegacyServers.newServer(servers.size)
        servers = servers + server
        LegacyServers.save(context, servers)
        selectServer(server.id)
    }

    /** Adds the player's own server to this profile's list, or updates it, and selects it. */
    fun addSoloServer(server: LegacyServer) {
        servers = if (servers.any { it.id == server.id }) servers.map { if (it.id == server.id) server else it } else servers + server
        LegacyServers.save(context, servers)
        selectServer(server.id)
    }

    fun removeServer(id: String) {
        if (servers.size < 2) return
        LegacyServers.vault(context, id).clear()
        servers = servers.filterNot { it.id == id }
        LegacyServers.save(context, servers)
        if (id == legacy.serverId) selectServer(servers.first().id)
        FastLogin.wipe(context)
    }

    fun saveCurseForgeKey(key: String): Boolean =
        CurseForgeAccess.saveKey(context, key).also { curseForge = CurseForgeAccess.load(context) }

    fun saveCurseForgeProxy(url: String): Boolean =
        CurseForgeAccess.saveProxy(context, url).also { curseForge = CurseForgeAccess.load(context) }

    fun forgetCurseForge() {
        CurseForgeAccess.clear(context)
        curseForge = CurseForgeAccess.load(context)
    }

    /** Launches the player's private server client from [legacy]. */
    fun launchLegacy(confirmedUnsupported: Boolean = false) {
        if (isLaunching) return
        if (!supportedGpu && !confirmedUnsupported) {
            unsupportedDeviceWarning = true
            return
        }
        val settings = legacy
        if (settings.saveLog) {
            LegacyClient.setLogWaiting(context, true)
            legacyLogToShare = null
            listOf(wineLog(), sharedLegacyLog(), File(settings.root, LegacyClient.LOG)).forEach { it.delete() }
        }
        val current = profile
        val solo = selectedServer.solo == LegacyServer.SOLO_DEVICE
        launch { screenSize, onStatus ->
            if (solo) runBlocking { SoloServer.start(context, onStatus) }
            if (current != null && settings.root.isNotBlank()) runCatching { ProfileAddons.applyMods(context, current, File(settings.root)) }.onFailure { Timber.w(it, "Applying client patches") }
            applyProfileAddons(File(settings.root, "WTF"))
            prepareLegacyLaunch(context, settings, gpu, screenSize, onStatus)
        }
    }

    private fun wineLog() = File(context.getExternalFilesDir(null), "wine_logs/wine_debug.log")
    private fun sharedLegacyLog() = File(context.externalCacheDir, "legacy_logs/${LegacyClient.LOG}")

    /**
     * Copies the last legacy launch's Wine log to the client folder and to the share cache, then asks the
     * player whether to share it. The previous log was deleted at launch. The launcher is rebuilt while the
     * game runs, so this runs both when it comes back and when the game ends; the copy is redone each time
     * until the player answers, so a copy taken mid-game is replaced by the full log.
     */
    private fun saveLegacyLog() {
        if (LegacyClient.clearLeftoverDebug(context)) PrefManager.enableWineDebug = false
        if (!LegacyClient.isLogWaiting(context)) return
        val log = wineLog()
        val root = legacy.root
        scope.launch(Dispatchers.IO) {
            runCatching {
                if (!log.isFile) return@runCatching
                if (root.isNotBlank()) log.copyTo(File(root, LegacyClient.LOG), overwrite = true)
                val shared = sharedLegacyLog().apply { parentFile?.mkdirs() }
                LegacyClient.trimLog(log, shared)
                legacyLogToShare = shared
            }.onFailure { Timber.w(it, "Couldn't copy the Wine log") }
        }
    }

    fun dismissLegacyLog() {
        legacyLogToShare = null
        LegacyClient.setLogWaiting(context, false)
    }

    /** Opens the share sheet for the last legacy log. Only called after the player said yes. */
    fun shareLegacyLog(activity: Context) {
        val file = legacyLogToShare ?: return
        dismissLegacyLog()
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, LegacyClient.LOG)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(send, "Share Wine log"))
    }

    fun applyHandheldGraphics() {
        val root = legacy.root.takeIf { it.isNotBlank() } ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { LegacyClient.applyHandheldGraphics(File(root)) }.isSuccess }
            legacyGraphicsApplied = ok
            if (!ok) errorMessage = "Couldn't write the client's Config.wtf."
        }
    }

    fun updateLegacy(settings: LegacySettings) {
        val rootChanged = settings.root != legacy.root
        val fastLoginOff = legacy.fastLogin && !settings.fastLogin
        legacy = settings
        LegacyClient.save(context, settings)
        LegacyServers.withSelected(servers, settings).takeIf { it != servers }?.let {
            servers = it
            LegacyServers.save(context, it)
        }
        if (fastLoginOff) FastLogin.wipe(context)
        if (rootChanged) {
            setProfileRoot(settings.root)
            detectLegacyBuild()
        }
    }

    fun detectLegacyBuild() {
        val root = legacy.root
        scope.launch {
            legacyBuild = withContext(Dispatchers.IO) {
                root.takeIf { it.isNotBlank() }?.let { LegacyClient.detectBuild(File(it, LegacyClient.EXE)) }
            }
        }
    }

    private fun launch(prepare: (screenSize: String, onStatus: (String) -> Unit) -> String) {
        isLaunching = true
        errorMessage = null
        statusText = "Preparing components..."
        launchJob = scope.launch {
            try {
                val screenSize = fitScreenSizeToWindow(context, gpu.screenSize)
                val containerId = withContext(Dispatchers.IO) {
                    prepare(screenSize) { msg -> scope.launch(Dispatchers.Main) { statusText = msg } }
                }
                statusText = "Booting into World of Warcraft..."
                onLaunch(containerId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: StorageAccessDeniedException) {
                resetLaunch()
                saveLegacyLog()
                needsStoragePermission = true
            } catch (e: Exception) {
                saveLegacyLog()
                Timber.e(e, "Error launching WoW Forever")
                errorMessage = e.message ?: "Launch failed"
                statusText = "Launch failed"
                isLaunching = false
            }
        }
    }

    fun resetLaunch() {
        launchJob?.cancel()
        launchJob = null
        isLaunching = false
        statusText = READY
    }

    fun performUpdate() {
        val target = versionStatus ?: return
        val product = files.product ?: return
        if (busy) return
        if (!files.hasStorageAccess) {
            needsStoragePermission = true
            return
        }
        isUpdating = true
        updateStatusText = "Connecting to Blizzard CDN..."
        errorMessage = null
        scope.launch {
            try {
                // The full installer, not upstream's binaries-only updater: patches also change the
                // client's .dat/.pak/.bin files (see the installer spike notes).
                val root = File(gamePath)
                GameInstaller().run(InstallRequest(root, product, installLocale(root, product))) { p ->
                    scope.launch(Dispatchers.Main) {
                        updateStatusText = when (p.phase) {
                            InstallProgress.Phase.RESOLVING -> "Reading build ${target.remoteVersion}..."
                            InstallProgress.Phase.DOWNLOADING -> p.fraction?.let { "Updating game files ${(it * 100).toInt()}%" } ?: "Updating game files..."
                            else -> "Finishing update..."
                        }
                    }
                }
                checkFiles()
                versionStatus = versionStatus?.copy(isOutdated = false, localVersion = target.remoteVersion)
                isUpdating = false
                launchGame()
            } catch (e: Exception) {
                Timber.e(e, "Error updating game")
                if (e.isPermissionDenied()) needsStoragePermission = true
                errorMessage = "Update failed: ${e.message}"
                isUpdating = false
            }
        }
    }

    /** Adds the launch product to a beta install, reusing its Data/, then downloads the launch client. */
    fun convertToLaunch() {
        if (busy || files.product != WowProduct.BETA) return
        isUpdating = true
        updateStatusText = "Switching to the launch version..."
        errorMessage = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val root = File(gamePath)
                    ProductConverter.applyLocal(root, WowProduct.BETA, WowProduct.FOREVER)
                    // The installer always asks the patch service for the launch build (and its KeyRing), so
                    // offline it fails cleanly instead of reusing the beta build keys cloned into the new row.
                    GameInstaller().run(InstallRequest(root, WowProduct.FOREVER, installLocale(root, WowProduct.BETA))) { p ->
                        scope.launch(Dispatchers.Main) {
                            updateStatusText = p.fraction?.let { "Downloading the launch version ${(it * 100).toInt()}%" } ?: "Preparing the launch version..."
                        }
                    }
                }
                canConvertToLaunch = false
                isUpdating = false
                useGameRoot(gamePath)
            } catch (e: Exception) {
                Timber.e(e, "Error converting to launch product")
                if (e.isPermissionDenied()) needsStoragePermission = true
                errorMessage = "Couldn't switch to the launch version: ${e.message}"
                isUpdating = false
            }
        }
    }

    fun performAppUpdate() {
        val target = appUpdate ?: return
        if (isDownloadingAppUpdate) return
        downloadedApk?.takeIf { it.exists() && it.length() == target.sizeBytes }?.let {
            installOrRequestPermission(it)
            return
        }
        isDownloadingAppUpdate = true
        appUpdateProgress = 0f
        errorMessage = null
        scope.launch {
            try {
                val apk = AppUpdater.download(context, target) { appUpdateProgress = it }
                downloadedApk = apk
                isDownloadingAppUpdate = false
                installOrRequestPermission(apk)
            } catch (e: Exception) {
                Timber.e(e, "Error downloading app update")
                errorMessage = "App update download failed: ${e.message}"
                isDownloadingAppUpdate = false
            }
        }
    }

    private fun installOrRequestPermission(apk: File) {
        if (AppUpdater.canInstall(context)) {
            AppUpdater.install(context, apk)
        } else {
            pendingInstallPermission = true
            needsInstallPermission = true
        }
    }

    fun openInstallPermissionSettings() {
        needsInstallPermission = false
        pendingInstallPermission = true
        AppUpdater.openInstallPermissionSettings(context)
    }

    fun openStorageSettings() {
        needsStoragePermission = false
        StorageUtils.requestManageExternalStoragePermission(context)
    }

    fun onResume() {
        // An install may have finished in the background service while another screen was showing: the
        // profile already points at its folder (see startedInstall), so checking the files picks it up.
        checkFiles()
        isLaunching = false
        statusText = READY
        if (gamePath.isNotBlank()) BattleNetSignIn.removeLoginFile(File(gamePath))
        FastLogin.wipe(context)
        if (versionStatus == null && !WoWLauncherState.shouldAutoLaunch) checkVersionStatus()
        if (appUpdate == null && !WoWLauncherState.shouldAutoLaunch) checkAppUpdate()
        if (pendingInstallPermission && AppUpdater.canInstall(context)) {
            pendingInstallPermission = false
            downloadedApk?.takeIf { it.exists() }?.let { AppUpdater.install(context, it) }
        }
    }

    fun onPause() {
        launchJob?.cancel()
        launchJob = null
    }

    fun onGameEnded() {
        resetLaunch()
        saveLegacyLog()
        if (gamePath.isNotBlank()) BattleNetSignIn.removeLoginFile(File(gamePath))
        FastLogin.wipe(context)
    }

    /** Upstream's start-up sequence: auto-launch only when files, login, game and app are all current. */
    suspend fun start() = coroutineScope {
        val ready = checkFiles()
        if (!files.hasStorageAccess && (files.dataExists || files.buildInfoExists)) needsStoragePermission = true
        when {
            isPrivate || !ready || !hasSavedLogin -> {
                WoWLauncherState.shouldAutoLaunch = false
                PluviaApp.events.emit(AndroidEvent.ClearBootingSplash)
                if (ready || isPrivate) {
                    checkVersionStatus()
                    checkAppUpdate()
                }
            }
            !WoWLauncherState.shouldAutoLaunch -> {
                checkVersionStatus()
                checkAppUpdate()
            }
            else -> {
                val updateJob = async { fetchAppUpdate() }
                fetchVersionStatus()
                val update = updateJob.await()
                WoWLauncherState.shouldAutoLaunch = false
                if (versionStatus?.isOutdated == true || update != null) {
                    PluviaApp.events.emit(AndroidEvent.ClearBootingSplash)
                } else if (supportedGpu) {
                    launchGame()
                } else {
                    PluviaApp.events.emit(AndroidEvent.ClearBootingSplash)
                }
            }
        }
    }

    // Read the install before the first frame, so screens built from this state never start out empty.
    init {
        checkFiles()
        if (isPrivate) detectLegacyBuild()
        saveLegacyLog()
    }

    private companion object {
        const val READY = "Ready to launch"
    }
}
