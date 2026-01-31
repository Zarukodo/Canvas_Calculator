package com.example.calculator

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants

// 1. 定義 Adapter，它需要一個「點擊事件」的 callback，當使用者點選某筆紀錄時觸發
class HistoryAdapter(
    private val settingsManager: SettingsManager,
    private val onItemClick: (HistoryItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private var historyList: List<HistoryItem> = emptyList()

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTimestamp: TextView = itemView.findViewById(R.id.tv_timestamp)
        val tvExpression: TextView = itemView.findViewById(R.id.tv_expression)
        val tvResult: TextView = itemView.findViewById(R.id.tv_result)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = historyList[position]

        holder.tvExpression.text = item.expression
        holder.tvResult.text = "= ${item.result}"
        holder.tvTimestamp.text = item.getFormattedTime()

        // Setting click event with feedback check
        holder.itemView.setOnClickListener {
            // 2. Execute Feedback logic based on settings
            if (settingsManager.isVibrationEnabled) {
                holder.itemView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            if (settingsManager.isSoundEnabled) {
                holder.itemView.playSoundEffect(SoundEffectConstants.CLICK)
            }

            // Trigger the actual logic
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = historyList.size

    fun submitList(newList: List<HistoryItem>) {
        historyList = newList
        notifyDataSetChanged()
    }
}