package hu.dashx

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

class AppEntry(val label: String, val pkg: String, val icon: Drawable)

object AppLister {
    fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(q, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }
}
