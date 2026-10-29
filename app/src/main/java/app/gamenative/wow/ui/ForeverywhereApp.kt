package app.gamenative.wow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.gamenative.PluviaApp
import app.gamenative.events.AndroidEvent
import app.gamenative.ui.screen.wow.BattleNetCredentialDialog
import app.gamenative.wow.AppName
import app.gamenative.wow.ui.addons.AddonsRoute
import app.gamenative.wow.ui.addons.addonsControllerFor
import app.gamenative.wow.ui.addons.legacyAddonsControllerFor
import app.gamenative.wow.ui.components.FwDialog
import app.gamenative.wow.ui.theme.Fw
import app.gamenative.wow.ui.theme.ForeverywhereTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Routes {
    const val HOME = "home"
    const val ADDONS = "addons"
    const val GAME = "game"
    const val CLIENT = "client"
    const val NEW_PROFILE = "newprofile"
    const val SETTINGS = "settings"
    const val FIRST_RUN = "firstrun"
    const val INSTALL = "install"
}

/** The app's own screens. Hosted by upstream's NavHost in place of `WoWForeverScreen`. */
@Composable
fun ForeverywhereApp(onLaunch: (appId: String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = remember { LauncherController(context, scope, onLaunch) }
    val nav = rememberNavController()
    var showLoginDialog by remember { mutableStateOf(false) }
    val addonsNow = remember(launcher.profile?.id, launcher.version, launcher.gamePath, launcher.files.product, launcher.legacy.root, launcher.curseForge) {
        val profile = launcher.addonProfile()
        if (launcher.isPrivate) {
            legacyAddonsControllerFor(launcher.legacy.root, launcher.version, scope, profile)
        } else {
            addonsControllerFor(launcher.gamePath, launcher.files.product, scope, launcher.curseForge, profile)
        }
    }
    LaunchedEffect(addonsNow) { addonsNow?.refreshInstalled() }
    // The nav graph is built once, so destinations read this state instead of capturing a stale value.
    val addonsState = rememberUpdatedState(addonsNow)

    // Decided once: recomputing it would rebuild the graph and drop first-run mid-way when an install appears.
    val startRoute = remember {
        when {
            launcher.profile == null -> Routes.NEW_PROFILE
            needsFirstRun(launcher) -> Routes.FIRST_RUN
            else -> Routes.HOME
        }
    }
    var appUpdateDismissed by remember { mutableStateOf(false) }
    val toHome = {
        if (!nav.popBackStack(Routes.HOME, inclusive = false)) nav.navigate(Routes.HOME) { popUpTo(nav.graph.id) { inclusive = true } }
    }
    val openGame = { nav.navigate(if (needsFirstRun(launcher)) Routes.FIRST_RUN else Routes.GAME) }
    val newProfile = { nav.navigate(Routes.NEW_PROFILE) }

    LaunchedEffect(Unit) { launcher.start() }
    LifecycleResumeEffect(launcher.gamePath) {
        launcher.onResume()
        onPauseOrDispose { launcher.onPause() }
    }
    DisposableEffect(Unit) {
        val reset: (Any) -> Unit = { launcher.onGameEnded() }
        PluviaApp.events.on<AndroidEvent.ForceCloseApp, Unit>(reset)
        PluviaApp.events.on<AndroidEvent.GuestProgramTerminated, Unit>(reset)
        onDispose {
            PluviaApp.events.off<AndroidEvent.ForceCloseApp, Unit>(reset)
            PluviaApp.events.off<AndroidEvent.GuestProgramTerminated, Unit>(reset)
        }
    }

    ForeverywhereTheme {
        Box(Modifier.fillMaxSize().background(Fw.Ground)) {
            NavHost(nav, startDestination = startRoute) {
                composable(Routes.HOME) {
                    HomeScreen(
                        launcher = launcher,
                        addonUpdates = addonsState.value?.updates,
                        addonCount = addonsState.value?.installed?.size,
                        onOpenAddons = { nav.navigate(Routes.ADDONS) },
                        onOpenGame = openGame,
                        onOpenClient = { nav.navigate(Routes.CLIENT) },
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                        onNewProfile = newProfile,
                    )
                }
                composable(Routes.GAME) {
                    if (launcher.isPrivate) {
                        LegacyServerScreen(launcher, onBack = { nav.popBackStack() })
                    } else {
                        GameFilesScreen(launcher, onBack = { nav.popBackStack() }, onInstall = { nav.navigate(Routes.INSTALL) })
                    }
                }
                composable(Routes.CLIENT) { LegacyClientScreen(launcher, onBack = { nav.popBackStack() }) }
                composable(Routes.SETTINGS) { SettingsScreen(launcher, onBack = { nav.popBackStack() }, onEditLogin = { showLoginDialog = true }) }
                composable(Routes.NEW_PROFILE) {
                    NewProfileScreen(
                        launcher,
                        onCreated = { version ->
                            toHome()
                            nav.navigate(if (version.isPrivateServer) Routes.CLIENT else Routes.FIRST_RUN)
                        },
                        // With no profile yet there is nothing behind this screen.
                        onBack = { if (launcher.profile != null && !nav.popBackStack()) toHome() },
                    )
                }
                composable(Routes.ADDONS) { AddonsRoute(launcher, addonsState.value, onBack = { nav.popBackStack() }) }
                composable(Routes.INSTALL) {
                    val back = { nav.popBackStack(); Unit }
                    FwPage("Install or repair", back) {
                        val repair = launcher.files.product?.takeIf { !launcher.filesMissing }?.let {
                            app.gamenative.wow.product.GameInstall(launcher.gamePath, it, launcher.versionStatus?.localVersion.orEmpty())
                        }
                        InstallStep(launcher, onDone = back, onBack = back, repair = repair)
                    }
                }
                composable(Routes.FIRST_RUN) {
                    FirstRunScreen(
                        launcher = launcher,
                        onEditLogin = { showLoginDialog = true },
                        onDone = toHome,
                        // "Playing another version of WoW?": a new profile replaces this one while it has no folder.
                        onOpenSettings = newProfile,
                    )
                }
            }
        }

        if (showLoginDialog) {
            BattleNetCredentialDialog(
                onDismiss = { showLoginDialog = false },
                onConfirm = {
                    showLoginDialog = false
                    launcher.onLoginSaved(it)
                },
            )
        }
        if (launcher.needsStoragePermission) {
            FwDialog(
                title = "Allow file access",
                message = "Android needs \"All files access\" so the app can install and update the game in this folder. Turn on \"Allow access to manage all files\" on the next screen.",
                confirm = "Open settings",
                onConfirm = launcher::openStorageSettings,
                onDismiss = { launcher.needsStoragePermission = false },
            )
        }
        launcher.appUpdate?.takeIf { !appUpdateDismissed && !launcher.isDownloadingAppUpdate }?.let { update ->
            FwDialog(
                title = "App update ${update.version}",
                message = "A new version of ${AppName.FULL} is available.",
                confirm = "Update now",
                onConfirm = {
                    appUpdateDismissed = true
                    launcher.performAppUpdate()
                },
                onDismiss = { appUpdateDismissed = true },
                dismiss = "Later",
            )
        }
        if (launcher.unsupportedDeviceWarning) {
            FwDialog(
                title = "This device can't run the game",
                message = "WoW Forever needs a Snapdragon chip with an Adreno GPU. On this device, including Android emulators, starting the game will most likely crash the app.",
                confirm = "Try anyway",
                onConfirm = {
                    launcher.unsupportedDeviceWarning = false
                    launcher.launchGame(confirmedUnsupported = true)
                },
                onDismiss = { launcher.unsupportedDeviceWarning = false },
            )
        }
        if (launcher.legacyLogToShare != null) {
            FwDialog(
                title = "Share the Wine log?",
                message = "The log from this launch shows why the client did or didn't start. Share opens the share sheet so you choose where it goes. The app sends nothing on its own, and the next launch replaces this log.",
                confirm = "Share",
                onConfirm = { launcher.shareLegacyLog(context) },
                onDismiss = launcher::dismissLegacyLog,
                dismiss = "Not now",
            )
        }
        if (launcher.needsInstallPermission) {
            FwDialog(
                title = "Allow app installs",
                message = "Android needs permission to install the update. Turn on \"Allow from this source\" on the next screen.",
                confirm = "Open settings",
                onConfirm = launcher::openInstallPermissionSettings,
                onDismiss = { launcher.needsInstallPermission = false },
            )
        }
    }
}

/** A WoW Forever or other Blizzard profile with no game folder yet starts at the first-run steps. */
private fun needsFirstRun(launcher: LauncherController) =
    !launcher.isPrivate && launcher.profile?.root.isNullOrBlank() && launcher.filesMissing
