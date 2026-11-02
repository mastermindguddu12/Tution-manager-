package com.teach.darbhanga.teacher

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.teach.darbhanga.teacher.databinding.ItemStudentBinding

class StudentAdapter(private val onClick: (Student) -> Unit) :
    RecyclerView.Adapter<StudentAdapter.VH>() {

    private var items: List<Student> = emptyList()

    class VH(val b: ItemStudentBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemStudentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val s = items[position]
        h.b.tvName.text = s.name
        h.b.tvBatch.text = s.batch.ifEmpty { "Batch nahi" }
        h.b.tvInfo.text = "📞 Parent: ${s.parentPhone.ifEmpty { "-" }}"
        h.b.tvFee.text = "₹${s.monthlyFee}"
        ProfileHelper.load(h.b.ivAvatar, s.photoUrl)
        h.b.root.setOnClickListener { onClick(s) }
    }

    fun submit(list: List<Student>) {
        items = list
        notifyDataSetChanged()
    }
}