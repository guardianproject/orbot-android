package org.torproject.android.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.torproject.android.R
import org.torproject.android.databinding.LayoutAppsItemBinding
import org.torproject.android.service.vpn.TorifiedAppWrapper

class AppManagerAdapter(
    private val onAppClicked: (TorifiedAppWrapper) -> Unit
) : ListAdapter<TorifiedAppWrapper, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val TYPE_APP = 0
        private const val TYPE_HEADER = 1
        private const val TYPE_SUBHEADER = 2

        private val DiffCallback = object : DiffUtil.ItemCallback<TorifiedAppWrapper>() {
            override fun areItemsTheSame(
                oldItem: TorifiedAppWrapper,
                newItem: TorifiedAppWrapper
            ): Boolean {
                if (oldItem.header != null || newItem.header != null)
                    return oldItem.header == newItem.header

                if (oldItem.subheader != null || newItem.subheader != null)
                    return oldItem.subheader == newItem.subheader

                return oldItem.app?.packageName == newItem.app?.packageName
            }

            override fun areContentsTheSame(
                oldItem: TorifiedAppWrapper,
                newItem: TorifiedAppWrapper
            ): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)

        return when {
            item.header != null -> TYPE_HEADER
            item.subheader != null -> TYPE_SUBHEADER
            else -> TYPE_APP
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(
                inflater.inflate(R.layout.item_app_header, parent, false)
            )
            TYPE_SUBHEADER -> SubHeaderHolder(
                inflater.inflate(R.layout.item_app_subheader, parent, false)
            )
            else -> AppHolder(
                LayoutAppsItemBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        when (holder) {
            is AppHolder -> holder.bind(getItem(position))
            is HeaderHolder -> holder.bind(getItem(position))
            is SubHeaderHolder -> holder.bind(getItem(position))
        }
    }

    inner class AppHolder(
        private val binding: LayoutAppsItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TorifiedAppWrapper) {
            val app = item.app ?: return

            binding.itemtext.text = app.name
            binding.itemcheck.apply {
                isChecked = app.isTorified

                setOnClickListener {
                    onAppClicked(item)
                }

                tag = app
            }

            binding.itemtext.setOnClickListener {
                onAppClicked(item)
            }

            binding.itemicon.setImageDrawable(
                try {
                    binding.root.context.packageManager
                        .getApplicationIcon(app.packageName)
                } catch (_: Exception) {
                    null
                }
            )
        }
    }

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(item: TorifiedAppWrapper) {
            itemView.findViewById<TextView>(R.id.header).text = item.header
        }
    }

    class SubHeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(item: TorifiedAppWrapper) {
            itemView.findViewById<TextView>(R.id.subheader).text = item.subheader
        }
    }
}
