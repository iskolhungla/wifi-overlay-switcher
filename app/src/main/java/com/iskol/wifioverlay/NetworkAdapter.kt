package com.iskol.wifioverlay

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class NetworkAdapter(
    private val onNetworkClick: (NetworkItem) -> Unit,
    private val onNetworkLongClick: (NetworkItem) -> Unit
) : RecyclerView.Adapter<NetworkAdapter.VH>() {

    private val items = mutableListOf<NetworkItem>()

    fun submit(newItems: List<NetworkItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wifi_network, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ssidView = itemView.findViewById<TextView>(R.id.tvSsid)
        private val signalTextView = itemView.findViewById<TextView>(R.id.tvSignalText)
        private val lockView = itemView.findViewById<ImageView>(R.id.ivLock)
        private val checkView = itemView.findViewById<ImageView>(R.id.ivConnected)
        private val signalView = itemView.findViewById<ImageView>(R.id.ivSignal)

        fun bind(item: NetworkItem) {
            ssidView.text = item.ssid
            ssidView.setTextColor(
                if (item.isSaved) 0xFF66BB6A.toInt() else 0xFFFFFFFF.toInt()
            )
            val widthText = item.channelWidthMhz?.let { " · ${it} МГц" } ?: ""
            signalTextView.text = "${item.level} дБм · ${dbmToPercent(item.level)}% · Канал ${frequencyToChannel(item.frequency)}$widthText"
            lockView.visibility = if (item.isSecured) View.VISIBLE else View.GONE
            checkView.visibility = if (item.isConnected) View.VISIBLE else View.GONE
            val iconRes = when {
                item.level >= -50 -> R.drawable.ic_signal_4
                item.level >= -60 -> R.drawable.ic_signal_3
                item.level >= -70 -> R.drawable.ic_signal_2
                else -> R.drawable.ic_signal_1
            }
            signalView.setImageResource(iconRes)
            itemView.setOnClickListener { onNetworkClick(item) }
            itemView.setOnLongClickListener {
                onNetworkLongClick(item)
                true
            }
        }
    }

    companion object {
        fun dbmToPercent(dbm: Int): Int = when {
            dbm <= -100 -> 0
            dbm >= -50 -> 100
            else -> 2 * (dbm + 100)
        }

        fun frequencyToChannel(freqMhz: Int): Int = when {
            freqMhz == 2484 -> 14
            freqMhz in 2412..2472 -> (freqMhz - 2412) / 5 + 1
            freqMhz in 5170..5825 -> (freqMhz - 5000) / 5
            freqMhz in 5955..7115 -> (freqMhz - 5950) / 5 + 1
            else -> 0
        }
    }
}
