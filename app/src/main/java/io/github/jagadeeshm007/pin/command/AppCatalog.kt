package io.github.jagadeeshm007.pin.command

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** A launcher entry: what the user sees and the package to open. */
data class LauncherApp(val label: String, val packageName: String)

/**
 * Finds the app a spoken "open <name>" means (spec PIN-30 AC-4). The list is loaded on
 * first use and reloaded once when a name is not found, so new installs are seen.
 * Call [find] off the main thread.
 */
class AppCatalog(private val load: () -> List<LauncherApp>) {
    @Volatile
    private var apps: List<LauncherApp>? = null

    /** Package for [name], or null when no single launcher app has that label. */
    fun find(name: String): String? {
        val cached = apps
        if (cached != null) match(name, cached)?.let { return it }
        return match(name, load().also { apps = it })
    }

    companion object {
        /** Exact match after normalising; spaces are ignored ("whats app"). Ambiguous is null. */
        fun match(name: String, apps: List<LauncherApp>): String? {
            val wanted = key(name)
            if (wanted.isEmpty()) return null
            val hits = apps.filter { key(it.label) == wanted }.map { it.packageName }.distinct()
            return hits.singleOrNull()
        }

        private fun key(text: String) = CommandGrammar.normalize(text).replace(" ", "")

        /** Catalog of the phone's launcher apps (visible through the manifest `<queries>`). */
        fun of(context: Context): AppCatalog = AppCatalog {
            val pm = context.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val found = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
            } else {
                pm.queryIntentActivities(launcher, 0) // the only form before API 33
            }
            found.map {
                LauncherApp(it.loadLabel(pm).toString(), it.activityInfo.packageName)
            }
        }
    }
}
