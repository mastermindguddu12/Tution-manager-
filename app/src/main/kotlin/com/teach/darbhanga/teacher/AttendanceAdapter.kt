package com.teach.darbhanga.teacher

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.teach.darbhanga.teacher.databinding.ItemAttendanceBinding

data class AttRow(val student: Student, var present: Boolean = true)

class AttendanceAdapter(private val onChange: () -> Unit) :
    RecyclerView.Adapter<AttendanceAdapter.VH>() {

    var items: List<AttRow> = emptyList()
        private set

    class VH(val b: ItemAttendanceBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemAttendanceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val row = items[position]
        h.b.tvName.text = row.student.name
        ProfileHelper.load(h.b.ivAvatar, row.student.photoUrl)
        paint(h, row.present)
        h.b.root.setOnClickListener {
            row.present = !row.present
            val pos = h.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos)
            onChange()
        }
    }

    private fun paint(h: VH, present: Boolean) {
        if (present) {
            h.b.tvStatus.text = "PRESENT"
            h.b.tvStatus.setTextColor(Color.parseColor("#2E7D32"))
            h.b.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E2F6EA"))
            h.b.root.strokeColor = Color.parseColor("#81C784")
            h.b.root.setCardBackgroundColor(Color.parseColor("#F6FCF8"))
        } else {
            h.b.tvStatus.text = "ABSENT"
            h.b.tvStatus.setTextColor(Color.parseColor("#C62828"))
            h.b.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFE4E6"))
            h.b.root.strokeColor = Color.parseColor("#E57373")
            h.b.root.setCardBackgroundColor(Color.parseColor("#FFF7F7"))
        }
    }

    fun submit(list: List<AttRow>) {
        items = list
        notifyDataSetChanged()
    }

    fun setAll(present: Boolean) {
        if (items.isEmpty()) return
        items.forEach { it.present = present }
        notifyDataSetChanged()
        onChange()
    }
}