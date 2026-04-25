package org.torproject.android.ui.connect

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.torproject.android.R
import org.torproject.android.localization.Languages
import org.torproject.android.ui.OrbotBottomSheetDialogFragment
import org.torproject.android.util.Prefs
import org.torproject.android.util.StringUtils
import java.text.Collator
import java.util.Locale
import java.util.TreeMap

class ExitNodeBottomSheet : OrbotBottomSheetDialogFragment() {

    private val sortedCountries = TreeMap<String, Locale>(Collator.getInstance())
    private lateinit var rvList: RecyclerView
    private lateinit var adapter: ExitNodeAdapter

    private var selectedCode: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.exit_node_bottom_sheet, container, false)

        // exitNodes returns {XY} for country but null for world
        selectedCode = Prefs.exitNodes
            ?.removePrefix("{")
            ?.removeSuffix("}")
            ?: ""
        rvList = view.findViewById(R.id.rvExitNodes)
        rvList.layoutManager = LinearLayoutManager(context)

        val items = mutableListOf<Pair<String, String>>()

        // Default world option
        items.add("" to getString(R.string.globe) + " " + getString(R.string.vpn_default_world))

        COUNTRY_CODES.forEach {
            val locale = Languages.buildLocaleForLanguage("", it)
            sortedCountries[locale.displayCountry] = locale
        }

        sortedCountries.forEach { (name, locale) ->
            val display = StringUtils.convertCountryCodeToFlagEmoji(locale.country) + " " + name
            items.add(locale.country to display)
        }

        adapter = ExitNodeAdapter(items)
        rvList.adapter = adapter

        return view
    }

    private inner class ExitNodeAdapter(
        private val list: List<Pair<String, String>>,
    ) : RecyclerView.Adapter<ExitNodeAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view.findViewById(R.id.tvCountry)
            val check: ImageView = view.findViewById(R.id.ivCheckmark)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_exit_node, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (code, displayName) = list[position]

            holder.text.text = displayName
            holder.check.visibility = if (code == selectedCode) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener {
                val prev = selectedCode
                selectedCode = code
                notifyItemChanged(list.indexOfFirst { it.first == prev })
                notifyItemChanged(position)
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY_EXIT_NODE_SELECTED,
                    Bundle().apply {
                        putString(BUNDLE_KEY_COUNTRY_CODE, code)
                    }
                )
                dismiss()
            }
        }

        override fun getItemCount() = list.size
    }

    companion object {
        const val REQUEST_KEY_EXIT_NODE_SELECTED = "request_key_exit_node_selected"
        const val BUNDLE_KEY_COUNTRY_CODE = "bundle_key_country_code"

        private val COUNTRY_CODES = arrayOf(
            "DE",
            "AT",
            "SE",
            "CH",
            "IS",
            "CA",
            "US",
            "ES",
            "FR",
            "BG",
            "PL",
            "AU",
            "BR",
            "CZ",
            "DK",
            "FI",
            "GB",
            "HU",
            "NL",
            "JP",
            "RO",
            "RU",
            "SG",
            "SK"
        )
    }
}
