package hu.dashx

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** App grid. With EXTRA_PICK_SLOT it acts as a picker for a launcher button. */
class AppDrawerActivity : Activity() {

    companion object {
        const val EXTRA_PICK_SLOT = "pick_slot"
    }

    private var apps: List<AppEntry> = emptyList()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = resources.displayMetrics.density
        val pick = intent.getIntExtra(EXTRA_PICK_SLOT, -1)
        if (pick >= 0) Toast.makeText(this, R.string.pick_app, Toast.LENGTH_LONG).show()

        val grid = GridView(this)
        grid.numColumns = GridView.AUTO_FIT
        grid.columnWidth = (110 * d).toInt()
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.verticalSpacing = (10 * d).toInt()
        val p = (12 * d).toInt()
        grid.setPadding(p, p, p, p)
        grid.clipToPadding = false
        grid.selector = ColorDrawable(0x22FFFFFF)
        grid.setBackgroundColor(0xFF080A0F.toInt())

        val adapter = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(i: Int): Any = apps[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, v: View?, parent: ViewGroup?): View {
                val row = v as? LinearLayout ?: LinearLayout(this@AppDrawerActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(0, (6 * d).toInt(), 0, (6 * d).toInt())
                    addView(ImageView(context), LinearLayout.LayoutParams((56 * d).toInt(), (56 * d).toInt()))
                    addView(TextView(context).apply {
                        setTextColor(Color.WHITE)
                        textSize = 13f
                        gravity = Gravity.CENTER
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                    })
                }
                val a = apps[i]
                (row.getChildAt(0) as ImageView).setImageDrawable(a.icon)
                (row.getChildAt(1) as TextView).text = a.label
                return row
            }
        }
        grid.adapter = adapter

        grid.setOnItemClickListener { _, _, i, _ ->
            val a = apps[i]
            if (pick >= 0) {
                Prefs.setSlot(this, pick, "app:" + a.pkg)
                finish()
            } else {
                packageManager.getLaunchIntentForPackage(a.pkg)?.let {
                    try {
                        startActivity(it)
                    } catch (_: Exception) {
                        Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        setContentView(grid)

        Thread {
            val l = AppLister.load(this)
            runOnUiThread {
                apps = l
                adapter.notifyDataSetChanged()
            }
        }.start()
    }
}
