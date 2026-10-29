package app.gamenative.wow.ui.addons

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.gamenative.BuildConfig
import app.gamenative.utils.Net
import app.gamenative.wow.addons.AddonCategory
import app.gamenative.wow.addons.AddonDb
import app.gamenative.wow.addons.AddonFlavor
import app.gamenative.wow.addons.AddonListing
import app.gamenative.wow.addons.AddonManager
import app.gamenative.wow.addons.CurseForgeSetup
import app.gamenative.wow.addons.addonTarget
import app.gamenative.wow.addons.provider.GitHubProvider
import app.gamenative.wow.legacy.LegacyGame
import app.gamenative.wow.addons.provider.WagoProvider
import app.gamenative.wow.addons.provider.WowInterfaceProvider
import app.gamenative.wow.product.GameVersion
import app.gamenative.wow.product.WowProduct
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** UI state for the addon browser, bound to one install's `Interface/AddOns` folder. */
class AddonsController(
    val addonsDir: File,
    val version: GameVersion,
    private val scope: CoroutineScope,
    curseForge: CurseForgeSetup = CurseForgeSetup(),
    private val profile: AddonProfile = AddonProfile.ALL,
) {
    enum class Mode { INSTALLED, BROWSE, CLIENT }

    constructor(gameRoot: File, product: WowProduct, scope: CoroutineScope, curseForge: CurseForgeSetup, profile: AddonProfile = AddonProfile.ALL) :
        this(File(gameRoot, "${product.flavorDir}/Interface/AddOns"), GameVersion.of(product), scope, curseForge, profile)

    /** CurseForge applies to this version but has no key or proxy set in Settings. */
    val curseForgeOff = !version.isPrivateServer && !curseForge.enabled

    /** Private servers only: add-ons that change the client itself. [addonsDir] is `<client>/Interface/AddOns`. */
    val clientMods = addonsDir.parentFile?.parentFile?.takeIf { version.isPrivateServer }
        ?.let { ClientModsController(it, scope, profile::modsChanged, LegacyGame.of(version)) }

    private val target = version.addonTarget()
    private val manager = AddonManager(
        addonsDir = addonsDir,
        db = AddonDb.forAddonsDir(addonsDir),
        providers = listOfNotNull(
            curseForge.provider(target).takeUnless { version.isPrivateServer },
            WagoProvider(BuildConfig.WAGO_API_KEY, target = target).takeUnless { version.isPrivateServer },
            WowInterfaceProvider(target = target),
            if (version.isPrivateServer) {
                GitHubProvider(target = target, catalogUrl = LegacyGame.of(version).catalogUrl)
            } else {
                GitHubProvider(target = target, fallbackCatalogUrl = GitHubProvider.UPSTREAM_CATALOG_URL)
            },
        ),
        http = Net.http,
        target = target,
    )

    var mode by mutableStateOf(Mode.INSTALLED)
        private set
    var query by mutableStateOf("")
        private set
    var includeClassic by mutableStateOf(true)
        private set
    var source by mutableStateOf<String?>(null)
        private set
    var category by mutableStateOf<AddonCategory?>(null)
        private set

    /** Long descriptions by addon key, loaded when an addon is selected. */
    var descriptions by mutableStateOf<Map<String, String?>>(emptyMap())
        private set
    private var descriptionJob: Job? = null
    var listings by mutableStateOf<List<AddonListing>>(emptyList())
        private set
    var installed by mutableStateOf<List<AddonListing>?>(null)
        private set
    var selected by mutableStateOf<AddonListing?>(null)
    var loading by mutableStateOf(false)
        private set
    var working by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    val updates get() = installed?.count { it.updateAvailable }
    val sources = listOf("curseforge", "wago", "wowinterface", "github")

    private var loadJob: Job? = null

    /** The installed addons this profile uses. */
    private suspend fun installedForProfile(): List<AddonListing> {
        val all = manager.installed()
        val visible = profile.visibility()
        return all.filter { listing -> listing.installed?.let { visible(it.id) } ?: true }
    }

    fun refreshInstalled() {
        scope.launch {
            installed = runCatching { withContext(Dispatchers.IO) { installedForProfile() } }
                .onFailure { Timber.w(it, "Listing installed addons failed") }
                .getOrDefault(emptyList())
            if (mode == Mode.INSTALLED) show(installed.orEmpty())
        }
    }

    fun switchMode(newMode: Mode) {
        mode = newMode
        selected = null
        when (newMode) {
            Mode.INSTALLED -> show(installed.orEmpty())
            Mode.BROWSE -> load()
            Mode.CLIENT -> Unit
        }
    }

    fun search(text: String) {
        query = text
        if (mode == Mode.INSTALLED) show(installed.orEmpty()) else load(debounce = true)
    }

    fun toggleClassic() {
        includeClassic = !includeClassic
        if (mode == Mode.INSTALLED) show(installed.orEmpty()) else load()
    }

    fun selectCategory(value: AddonCategory?) {
        category = value
        if (mode == Mode.INSTALLED) show(installed.orEmpty()) else load()
    }

    fun keyOf(listing: AddonListing) = "${listing.remote.provider}:${listing.remote.projectId}"

    /** Fetches the long description for [listing] once; shows the summary until it arrives. */
    fun loadDescription(listing: AddonListing) {
        val key = keyOf(listing)
        if (key in descriptions) return
        descriptionJob?.cancel()
        descriptionJob = scope.launch {
            delay(250)
            val text = runCatching { withContext(Dispatchers.IO) { manager.description(listing) } }.getOrNull()
            descriptions = descriptions + (key to text)
        }
    }

    fun cycleSource() {
        source = (listOf<String?>(null) + sources).let { all -> all[(all.indexOf(source) + 1) % all.size] }
        if (mode == Mode.INSTALLED) show(installed.orEmpty()) else load()
    }

    private fun load(debounce: Boolean = false) {
        loadJob?.cancel()
        loadJob = scope.launch {
            if (debounce) delay(400)
            loading = true
            try {
                val found = withContext(Dispatchers.IO) {
                    if (query.isBlank()) manager.popular(includeClassic, category) else manager.search(query, includeClassic, category)
                }
                show(found)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Couldn't reach the addon sites. Check your connection."
                Timber.w(e, "Addon search failed")
            } finally {
                loading = false
            }
        }
    }

    private fun show(items: List<AddonListing>) {
        val q = query.trim().lowercase()
        listings = items.filter { listing ->
            (includeClassic || listing.bestFlavor != AddonFlavor.FALLBACK) &&
                (source == null || source in listing.sources) &&
                (mode == Mode.BROWSE || category == null || category in listing.remote.categories) &&
                (mode == Mode.BROWSE || q.isEmpty() || q in listing.remote.name.lowercase())
        }
        selected = selected?.let { sel -> listings.firstOrNull { it.remote.projectId == sel.remote.projectId } } ?: listings.firstOrNull()
    }

    fun install(listing: AddonListing) = act("Installing ${listing.remote.name}") { markInstalled(listing, manager.install(listing.remote)) }

    fun update(listing: AddonListing) = act("Updating ${listing.remote.name}") {
        val record = listing.installed ?: return@act
        val updated = if (record.flavor == AddonFlavor.FALLBACK && listing.bestFlavor == AddonFlavor.MAIN) manager.install(listing.remote) else manager.update(record)
        markInstalled(listing, updated)
    }

    /** Shows the new state right away; the full refresh after the action fills in the rest. */
    /** The record an install or update just wrote, claimed even when its id was already on disk. */
    @Volatile
    private var installedNow: String? = null

    private suspend fun markInstalled(listing: AddonListing, record: app.gamenative.wow.addons.InstalledAddon) = withContext(Dispatchers.Main) {
        installedNow = record.id
        val key = keyOf(listing)
        val patch = { l: AddonListing -> if (keyOf(l) == key) l.copy(installed = record, updateAvailable = false) else l }
        listings = listings.map(patch)
        selected = selected?.let(patch)
    }

    fun remove(listing: AddonListing) = act("Removing ${listing.remote.name}") {
        listing.installed?.let { if (!profile.release(it)) manager.remove(it) }
    }

    fun updateAll() = act("Updating all addons") { manager.updateAll() }

    fun installFromUrl(url: String) = act("Installing from link") { installedNow = manager.installFromUrl(url.trim()).id }

    private fun act(label: String, block: suspend () -> Unit) {
        if (working != null) return
        working = label
        message = null
        val db = AddonDb.forAddonsDir(addonsDir)
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val before = db.load().map { it.id }.toSet()
                    block()
                    // What this action installed, dependencies included, belongs to this profile.
                    profile.claim(db.load().map { it.id }.toSet() - before + listOfNotNull(installedNow))
                    installedNow = null
                }
                message = "$label: done."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, label)
                message = "$label failed: ${e.message ?: "unknown error"}"
            } finally {
                working = null
                installed = runCatching { withContext(Dispatchers.IO) { installedForProfile() } }.getOrDefault(installed.orEmpty())
                if (mode == Mode.INSTALLED) show(installed.orEmpty()) else show(listings.map { l -> refreshed(l) })
            }
        }
    }

    private fun refreshed(listing: AddonListing): AddonListing {
        val record = installed?.firstOrNull { it.remote.provider == listing.remote.provider && it.remote.projectId == listing.remote.projectId }
        return listing.copy(installed = record?.installed, updateAvailable = record?.updateAvailable ?: false)
    }
}

/** How the addon screen sees a game folder that several profiles may share. Every addon belongs to everyone by default. */
interface AddonProfile {
    /** Which installed addon ids the profile uses; read again after each change. */
    fun visibility(): (String) -> Boolean

    fun claim(ids: Set<String>)

    /** True when the addon's files must stay because another profile uses it. */
    fun release(addon: app.gamenative.wow.addons.InstalledAddon): Boolean

    fun modsChanged()

    companion object {
        val ALL = object : AddonProfile {
            override fun visibility(): (String) -> Boolean = { true }
            override fun claim(ids: Set<String>) = Unit
            override fun release(addon: app.gamenative.wow.addons.InstalledAddon) = false
            override fun modsChanged() = Unit
        }
    }
}
