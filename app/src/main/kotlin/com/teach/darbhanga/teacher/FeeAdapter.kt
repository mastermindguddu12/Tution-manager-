package com.teach.darbhanga.teacher

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.teach.darbhanga.teacher.databinding.ItemFeeBinding

class FeeAdapter(private val onClick: (FeeRow) -> Unit) :
    RecyclerView.Adapter<FeeAdapter.VH>() {

    private var items: List<FeeRow> = emptyList()

    class VH(val b: ItemFeeBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemFeeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = items[position]
        h.b.tvName.text = r.name
        h.b.tvInfo.text = "${r.batch}  •  📞 ${r.parentPhone.ifEmpty { "-" }}"
        h.b.tvAmount.text = "₹${r.amount}"
        ProfileHelper.load(h.b.ivAvatar, r.photoUrl)

        if (r.paid) {
            h.b.tvStatus.text = "PAID"
            h.b.tvStatus.setTextColor(Color.parseColor("#2E7D32"))
            h.b.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E2F6EA"))
        } else {
            h.b.tvStatus.text = "PENDING"
            h.b.tvStatus.setTextColor(Color.parseColor("#C62828"))
            h.b.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFE4E6"))
        }
        h.b.root.setOnClickListener { onClick(r) }
    }

    fun submit(list: List<FeeRow>) {
        items = list
        notifyDataSetChanged()
    }
}