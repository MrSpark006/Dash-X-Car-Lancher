package hu.dashx

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast

enum class Glyph { RADIO, GRID, PLUS }

class TileInfo(val label: String, val icon: Drawable?, val glyph: Glyph)

object Actions {

    fun intentFor(ctx: Context, spec: String): Intent? {
        val i: Intent? = when {
            spec == "intent:navi" -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))
            spec == "intent:music" ->
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
            spec == "intent:phone" -> Intent(Intent.ACTION_DIAL)
            spec == "intent:web" ->
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
            spec.startsWith("app:") ->
                ctx.packageManager.getLaunchIntentForPackage(spec.removePrefix("app:"))
            else -> null
        }
        return i?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun info(ctx: Context, spec: String): TileInfo {
        val pm = ctx.packageManager
        return when {
            spec == "builtin:radio" -> TileInfo(ctx.getString(R.string.t_radio), null, Glyph.RADIO)
            spec == "builtin:drawer" -> TileInfo(ctx.getString(R.string.t_apps), null, Glyph.GRID)
            spec.startsWith("intent:") -> {
                val labelRes = when (spec) {
                    "intent:navi" -> R.string.t_navi
                    "intent:music" -> R.string.t_music
                    "intent:phone" -> R.string.t_phone
                    else -> R.string.t_web
                }
                val icon = try {
                    val i = intentFor(ctx, spec)
                    val probe = i?.selector ?: i
                    if (probe == null) null else
                        pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
                            ?.takeIf { it.activityInfo.packageName != "android" }
                            ?.loadIcon(pm)
                } catch (_: Exception) {
                    null
                }
                TileInfo(ctx.getString(labelRes), icon, Glyph.PLUS)
            }
            spec.startsWith("app:") -> try {
                val ai = pm.getApplicationInfo(spec.removePrefix("app:"), 0)
                TileInfo(ai.loadLabel(pm).toString().uppercase(), ai.loadIcon(pm), Glyph.PLUS)
            } catch (_: Exception) {
                TileInfo("?", null, Glyph.PLUS)
            }
            else -> TileInfo("?", null, Glyph.PLUS)
        }
    }

    fun run(ctx: Context, spec: String) {
        try {
            when (spec) {
                "builtin:radio" -> ctx.startActivity(Intent(ctx, RadioActivity::class.java))
                "builtin:drawer" -> ctx.startActivity(Intent(ctx, AppDrawerActivity::class.java))
                else -> {
                    val i = intentFor(ctx, spec)
                    if (i != null) ctx.startActivity(i)
                    else Toast.makeText(ctx, R.string.cant_open, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: Exception) {
            Toast.makeText(ctx, R.string.cant_open, Toast.LENGTH_SHORT).show()
        }
    }
}
