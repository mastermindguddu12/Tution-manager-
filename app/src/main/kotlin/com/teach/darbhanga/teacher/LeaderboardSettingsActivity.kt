package com.teach.darbhanga.teacher

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.teach.darbhanga.teacher.databinding.ActivityLbSettingsBinding
import com.teach.darbhanga.teacher.databinding.ItemRewardFieldBinding
import kotlinx.coroutines.launch

class LeaderboardSettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivityLbSettingsBinding
    private var winners = 2
    private val texts = MutableList(5) { "" }
    private val fields = mutableListOf<ItemRewardFieldBinding>()
    private val pills = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLbSettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.tvBack.setOnClickListener { finish() }
        b.btnSave.setOnClickListener { save() }

        buildPills()
        buildFields()

        lifecycleScope.launch {
            try {
                val s = LeaderboardEngine.loadSettings()
                winners = s.winners
                s.rewards.forEachIndexed { i, t -> if (i < 5) texts[i] = t }
                b.etTuition.setText(s.tuitionReward)
                stylePills()
                buildFields()
            } catch (e: Exception) {
                toast("Purani settings load nahi hui: ${e.localizedMessage}")
            }
        }
    }

    private fun buildPills() {
        b.llWinners.removeAllViews()
        pills.clear()
        for (n in 1..5) {
            val tv = TextView(this)
            tv.text = "Top $n"
            tv.gravity = Gravity.CENTER
            tv.textSize = 12f
            tv.setTypeface(null, Typeface.BOLD)
            tv.setBackgroundResource(R.drawable.bg_chip)
            val lp = LinearLayout.LayoutParams(0, UiHelper.dp(this, 42), 1f)
            lp.setMargins(UiHelper.dp(this, 3), 0, UiHelper.dp(this, 3), 0)
            tv.layoutParams = lp
            tv.setOnClickListener {
                captureTexts()
                winners = n
                stylePills()
                buildFields()
            }
            b.llWinners.addView(tv)
            pills.add(tv)
        }
        stylePills()
    }

    private fun stylePills() {
        pills.forEachIndexed { i, tv ->
            val sel = (i + 1) == winners
            tv.setTextColor(UiHelper.color(this, if (sel) R.color.white else R.color.text_gray))
            tv.backgroundTintList = ColorStateList.valueOf(
                UiHelper.color(this, if (sel) R.color.indigo else R.color.t_gray)
            )
        }
    }

    private fun captureTexts() {
        fields.forEachIndexed { i, f -> texts[i] = f.etReward.text.toString() }
    }

    private fun buildFields() {
        b.llRewards.removeAllViews()
        fields.clear()
        for (i in 0 until winners) {
            val f = ItemRewardFieldBinding.inflate(layoutInflater, b.llRewards, false)
            f.til.hint = "${LbUi.medal(i + 1)}  Rank ${i + 1} ka inaam (jaise Pen set)"
            f.etReward.setText(texts[i])
            b.llRewards.addView(f.root)
            fields.add(f)
        }
    }

    private fun save() {
        captureTexts()
        val s = LbSettings(
            winners = winners,
            rewards = texts.take(winners).map { it.trim() },
            tuitionReward = b.etTuition.text.toString().trim()
        )
        b.btnSave.isEnabled = false
        lifecycleScope.launch {
            try {
                LeaderboardEngine.saveSettings(s)
                toast("Inaam save ho gaye")
                finish()
            } catch (e: Exception) {
                b.btnSave.isEnabled = true
                toast("Save fail: ${e.localizedMessage}")
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}