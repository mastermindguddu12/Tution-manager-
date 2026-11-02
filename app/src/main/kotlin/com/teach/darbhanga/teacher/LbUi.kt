package com.teach.darbhanga.teacher

import android.view.LayoutInflater
import android.widget.LinearLayout
import com.teach.darbhanga.teacher.databinding.ItemLbMiniBinding
import java.text.SimpleDateFormat
import java.util.Locale

object LbUi {

    fun medal(rank: Int): String = when (rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "#$rank"
    }

    fun monthLabel(ym: String): String = try {
        val d = SimpleDateFormat("yyyy-MM", Locale.US).parse(ym)
        SimpleDateFormat("MMMM yyyy", Locale.US).format(d!!)
    } catch (e: Exception) {
        ym
    }

    fun addMini(container: LinearLayout, r: LbRanked, sub: String) {
        val m = ItemLbMiniBinding.inflate(LayoutInflater.from(container.context), container, false)
        m.tvMedal.text = medal(r.rank)
        m.tvName.text = r.e.name
        m.tvSub.text = sub
        m.tvScore.text = fmtNum(r.e.score)
        ProfileHelper.load(m.ivAvatar, r.e.photo)
        container.addView(m.root)
    }
}