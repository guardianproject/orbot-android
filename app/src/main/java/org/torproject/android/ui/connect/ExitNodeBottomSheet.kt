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
import org.torproject.android.service.util.Prefs
import org.torproject.android.service.util.Utils
import org.torproject.android.ui.OrbotBottomSheetDialogFragment

import java.text.Collator
import java.util.Locale
import java.util.TreeMap

class ExitNodeBottomSheet(
    private val callback: ExitNodeSelectedCallback
) : OrbotBottomSheetDialogFragment() {

    interface ExitNodeSelectedCallback {
        fun onExitNodeSelected(countryCode: String, displayCountryName: String)
    }

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

        selectedCode = Prefs.getExitNodes().removePrefix("{").removeSuffix("}")

        rvList = view.findViewById(R.id.rvExitNodes)
        rvList.layoutManager = LinearLayoutManager(context)

        val items = mutableListOf<Pair<String, String>>()

        // Default world option
        items.add("" to getString(R.string.globe) + " " + getString(R.string.vpn_default_world))

        COUNTRY_CODES.forEach {
            val locale = Locale("", it)
            sortedCountries[locale.displayCountry] = locale
        }

        sortedCountries.forEach { (name, locale) ->
            val display = Utils.convertCountryCodeToFlagEmoji(locale.country) + " " + name
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
                callback.onExitNodeSelected(code, displayName)
                dismiss()
            }
        }

        override fun getItemCount() = list.size
    }

    companion object {
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
