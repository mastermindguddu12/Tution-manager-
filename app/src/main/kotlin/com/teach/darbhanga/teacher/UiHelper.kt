package com.teach.darbhanga.teacher

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.widget.GridLayout
import androidx.core.content.ContextCompat
import com.teach.darbhanga.teacher.databinding.ItemTileBinding

object UiHelper {

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun color(ctx: Context, res: Int): Int = ContextCompat.getColor(ctx, res)

    fun addTile(
        grid: GridLayout,
        emoji: String,
        title: String,
        sub: String,
        tintRes: Int,
        span: Int = 1,
        onClick: () -> Unit
    ) {
        val ctx = grid.context
        val t = ItemTileBinding.inflate(LayoutInflater.from(ctx), grid, false)
        t.tvIcon.text = emoji
        t.tvIcon.backgroundTintList = ColorStateList.valueOf(color(ctx, tintRes))
        t.tvTitle.text = title
        t.tvSub.text = sub
        t.root.setOnClickListener { onClick() }

        val lp = GridLayout.LayoutParams(
            GridLayout.spec(GridLayout.UNDEFINED),
            GridLayout.spec(GridLayout.UNDEFINED, span, 1f)
        )
        lp.width = 0
        lp.setMargins(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 6))
        t.root.layoutParams = lp
        grid.addView(t.root)
    }
}