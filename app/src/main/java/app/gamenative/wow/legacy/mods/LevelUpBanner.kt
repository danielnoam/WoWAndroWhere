package app.gamenative.wow.legacy.mods

import java.io.File

/** Our level up banner: a plain add-on in the client's AddOns folder, copied from the app's resources. */
object LevelUpBanner {
    const val FOLDER = "AndroWhere_LevelUp"
    private val FILES = listOf("$FOLDER.toc", "LevelUp.lua")

    private fun dir(root: File) = File(root, "Interface/AddOns/$FOLDER")

    fun installed(root: File) = File(dir(root), "$FOLDER.toc").isFile

    fun install(root: File) {
        val dir = dir(root).apply { mkdirs() }
        FILES.forEach { name ->
            val bytes = checkNotNull(javaClass.getResourceAsStream("/everywhere/addons/$FOLDER/$name")) { "Missing $name" }.use { it.readBytes() }
            File(dir, name).writeBytes(bytes)
        }
    }

    fun remove(root: File) {
        dir(root).deleteRecursively()
    }
}
