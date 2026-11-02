package com.teach.darbhanga.teacher

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.teach.darbhanga.teacher.databinding.ItemLbRowBinding

class LbAdapter : RecyclerView.Adapter<LbAdapter.VH>() {

    private var items: List<LbRanked> = emptyList()
    private var myId = ""

    class VH(val b: ItemLbRowBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemLbRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    private fun pct(v: Double): String = if (v < 0) "-" else "${v.toInt()}%"

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = items[position]
        val e = r.e
        val ctx = h.b.root.context

        h.b.tvRank.text = LbUi.medal(r.rank)
        h.b.tvName.text = e.name + if (e.id == myId) "  (Aap)" else ""
        h.b.tvSub.text = "${e.batch}  •  Test ${pct(e.tests)}  Quiz ${pct(e.quiz)}  Att ${pct(e.att)}"
        h.b.tvScore.text = fmtNum(e.score)
        ProfileHelper.load(h.b.ivAvatar, e.photo)

        when {
            e.id == myId -> {
                h.b.root.strokeColor = Color.parseColor("#3F37C9")
                h.b.root.strokeWidth = UiHelper.dp(ctx, 2)
                h.b.root.setCardBackgroundColor(Color.parseColor("#F0EFFF"))
            }
            r.rank <= 3 -> {
                h.b.root.strokeColor = Color.parseColor("#F5B83D")
                h.b.root.strokeWidth = UiHelper.dp(ctx, 2)
                h.b.root.setCardBackgroundColor(Color.parseColor("#FFFBF0"))
            }
            else -> {
                h.b.root.strokeWidth = 0
                h.b.root.setCardBackgroundColor(Color.WHITE)
            }
        }
    }

    fun submit(list: List<LbRanked>, me: String) {
        items = list
        myId = me
        notifyDataSetChanged()
    }
}