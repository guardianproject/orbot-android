package org.torproject.android.ui

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.recyclerview.widget.RecyclerView

import org.torproject.android.R
import org.torproject.android.service.vpn.TorifiedAppWrapper
import org.torproject.android.ui.AppManagerActivity.AppViewHolder

class AppManagerAdapter(
    private var data: MutableList<TorifiedAppWrapper>,
    private val packageManager: PackageManager,
    private val onClickListener: View.OnClickListener,
) : RecyclerView.Adapter<AppViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.layout_apps_item, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val item = data[position]

        with(holder) {
            when {
                item.header != null -> {
                    header.apply {
                        text = item.header
                        visibility = View.VISIBLE
                    }
                    subheader.visibility = View.GONE
                    container.visibility = View.GONE
                }
                item.subheader != null -> {
                    subheader.apply {
                        text = item.subheader
                        visibility = View.VISIBLE
                    }
                    header.visibility = View.GONE
                    container.visibility = View.GONE
                }
                else -> {
                    val app = item.app
                    header.visibility = View.GONE
                    subheader.visibility = View.GONE
                    container.visibility = View.VISIBLE

                    try {
                        icon.apply {
                            setImageDrawable(packageManager.getApplicationIcon(app!!.packageName))
                            tag = box
                            setOnClickListener(onClickListener)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    text.apply {
                        text = app!!.name
                        tag = box
                        setOnClickListener(onClickListener)
                    }

                    box.apply {
                        isChecked = app!!.isTorified
                        tag = app
                        setOnClickListener(onClickListener)
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = data.size

    fun setData(newData: List<TorifiedAppWrapper>) {
        data.clear()
        data.addAll(newData)
        notifyDataSetChanged()
    }
}
