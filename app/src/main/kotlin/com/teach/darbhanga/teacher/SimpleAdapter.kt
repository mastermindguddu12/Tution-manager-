package com.teach.darbhanga.teacher

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.teach.darbhanga.teacher.databinding.ItemSimpleBinding
import java.util.Locale

data class SimpleItem(
    val id: String,
    val title: String,
    val sub: String,
    val status: String = "",
    val statusColor: String = "#455A64",
    val extra: Map<String, Any?> = emptyMap()
)

fun fmtNum(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

class SimpleAdapter(private val onClick: (SimpleItem) -> Unit) :
    RecyclerView.Adapter<SimpleAdapter.VH>() {

    private var items: List<SimpleItem> = emptyList()

    class VH(val b: ItemSimpleBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSimpleBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val it = items[position]
        h.b.tvTitle.text = it.title
        h.b.tvSub.text = it.sub
        h.b.tvStatus.text = it.status
        h.b.tvStatus.setTextColor(Color.parseColor(it.statusColor))
        h.b.root.setOnClickListener { _ -> onClick(it) }
    }

    fun submit(list: List<SimpleItem>) {
        items = list
        notifyDataSetChanged()
    }
}