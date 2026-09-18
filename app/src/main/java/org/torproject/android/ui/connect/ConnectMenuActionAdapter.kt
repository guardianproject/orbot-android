package org.torproject.android.ui.connect

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import org.torproject.android.R
import org.torproject.android.Regionalization
import org.torproject.android.ui.OrbotMenuAction
import org.torproject.android.util.Prefs
import org.torproject.android.util.Settings
import java.util.TreeMap

class ConnectMenuActionAdapter(context: Context, list: ArrayList<OrbotMenuAction>) :
    ArrayAdapter<OrbotMenuAction>(context, R.layout.action_list_view, list) {

    private val layoutInflater =
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    @SuppressLint("InflateParams")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        if (convertView != null) return convertView
        val returnView = convertView ?: layoutInflater.inflate(R.layout.action_list_view, null)
        getItem(position)?.let { model ->
            val imgView = returnView.findViewById<ImageView>(R.id.ivAction)
            val emojiContainer = returnView.findViewById<FrameLayout>(R.id.textContainer)
            val tvAction = returnView.findViewById<TextView>(R.id.tvEmoji)
            val hvApps = returnView.findViewById<HorizontalScrollView>(R.id.llBoxShortcuts)

            when (model.imgId) {
                R.drawable.ic_choose_apps -> {
                    emojiContainer.visibility = View.GONE
                    imgView.visibility = View.VISIBLE
                    imgView.setImageResource(model.imgId)
                    if (!drawAppShortcuts(hvApps)) {
                        returnView.findViewById<TextView>(R.id.llBoxShortcutsText).visibility =
                            View.VISIBLE
                    }

                }

                0 -> {
                    imgView.visibility = View.GONE
                    val currentExit = Prefs.exitNodes?.replace("{", "")?.replace("}", "") ?: ""
                    if (currentExit.length == 2) tvAction.text =
                        Regionalization.getFlagEmojiForCountryCode(currentExit)
                    else tvAction.text = context.getString(R.string.globe)
                    emojiContainer.visibility = View.VISIBLE
                }

                else -> {
                    emojiContainer.visibility = View.GONE
                    imgView.visibility = View.VISIBLE
                    imgView.setImageResource(model.imgId)
                }
            }
            if (model.statusString?.isEmpty() == true)
                returnView.findViewById<TextView>(R.id.tvLabel).text =
                    context.getString(model.textId)
            else
                returnView.findViewById<TextView>(R.id.tvLabel).text = model.statusString

            returnView.setOnClickListener { model.action() }
        }
        return returnView
    }

    private fun drawAppShortcuts(llBoxShortcuts: HorizontalScrollView): Boolean {

        val tordAppString = Settings.torifiedApps
        if (!TextUtils.isEmpty(tordAppString)) {

            val packageManager: PackageManager = context.packageManager
            val tordApps = tordAppString.split("|").toTypedArray()
            val container = llBoxShortcuts.getChildAt(0) as LinearLayout

            llBoxShortcuts.visibility = View.VISIBLE
            container.removeAllViews()
            val icons: MutableMap<String, ImageView> = TreeMap()
            for (tordApp in tordApps) {

                if (tordApp.isNotEmpty()) {
                    try {
                        packageManager.getPackageInfo(tordApp, 0)
                        val iv = ImageView(context)
                        val applicationInfo = packageManager.getApplicationInfo(tordApp, 0)
                        iv.setImageDrawable(packageManager.getApplicationIcon(tordApp))
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.height = 50
                        params.width = 50
                        params.marginEnd = 20
                        params.gravity = Gravity.CENTER
                        iv.layoutParams = params

                        iv.setOnClickListener { _: View? ->
                            checkTorStatusUrl(context, applicationInfo.packageName)
                        }
                        icons[packageManager.getApplicationLabel(applicationInfo).toString()] = iv
                    } catch (_: PackageManager.NameNotFoundException) {
                        //couldn't draw icon for the package name
                        Log.d("ConnectMenuAdapter", "error getting pkg info for: $tordApp")

                    }
                }
            }
            if (icons.isNotEmpty()) {
                val sorted = TreeMap(icons)
                for (iv in sorted.values) container.addView(iv)
            }
            return true
        }

        return false
    }

    private fun checkTorStatusUrl(context: Context, pkg: String) {
        val i = Intent().apply {
            `package` = pkg
            data = "https://check.torproject.org".toUri()
            action = Intent.ACTION_VIEW
        }
        val pm: PackageManager = context.packageManager
        if (i.resolveActivity(pm) != null) context.startActivity(i)
    }

}