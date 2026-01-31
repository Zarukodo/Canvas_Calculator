package com.example.calculator

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView

class ColorPaletteAdapter(
    private var colors: MutableList<Int>,
    private var selectedColor: Int,
    private val onColorClick: (Int) -> Unit,
    private val onAddClick: () -> Unit,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_COLOR = 0
        private const val TYPE_ADD = 1
        private const val MAX_COLORS = 15
    }

    // 更新資料用
    fun updateData(newColors: List<Int>, newSelected: Int) {
        colors.clear()
        colors.addAll(newColors)
        selectedColor = newSelected
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        // 如果列表還沒滿，最後一個位置顯示「新增按鈕」
        return if (position == colors.size && colors.size < MAX_COLORS) {
            TYPE_ADD
        } else {
            TYPE_COLOR
        }
    }

    override fun getItemCount(): Int {
        // 如果還沒滿 12 個，就多顯示一個 "+" 按鈕
        return if (colors.size < MAX_COLORS) colors.size + 1 else colors.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_color_circle, parent, false)
        return if (viewType == TYPE_ADD) AddViewHolder(view) else ColorViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ColorViewHolder) {
            if (position in colors.indices) {
                val color = colors[position]
                holder.bind(color, color == selectedColor)
            }
        } else if (holder is AddViewHolder) {
            holder.bind()
        }
    }

    // === ViewHolder: 顯示顏色 ===
    inner class ColorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivColor: ShapeableImageView = itemView.findViewById(R.id.iv_color_circle)
        private val border: View = itemView.findViewById(R.id.view_border)
        private val ivAdd: ImageView = itemView.findViewById(R.id.iv_add_icon)

        fun bind(color: Int, isSelected: Boolean) {
            ivAdd.visibility = View.GONE
            ivColor.setBackgroundColor(color)

            // 選中狀態顯示外框
            border.visibility = if (isSelected) View.VISIBLE else View.GONE

            // 點擊套用
            itemView.setOnClickListener { onColorClick(color) }

            // 長按刪除
            itemView.setOnLongClickListener {
                onDeleteClick(color)
                true
            }
        }
    }

    // === ViewHolder: 顯示新增按鈕 ===
    inner class AddViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivColor: ShapeableImageView = itemView.findViewById(R.id.iv_color_circle)
        private val border: View = itemView.findViewById(R.id.view_border)
        private val ivAdd: ImageView = itemView.findViewById(R.id.iv_add_icon)

        fun bind() {
            border.visibility = View.GONE
            ivColor.setBackgroundColor(Color.parseColor("#EEEEEE")) // 淺灰底
            ivAdd.visibility = View.VISIBLE

            itemView.setOnClickListener { onAddClick() }
        }
    }
}