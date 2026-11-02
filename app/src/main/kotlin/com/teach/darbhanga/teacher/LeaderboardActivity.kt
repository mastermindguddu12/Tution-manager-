package com.teach.darbhanga.teacher

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.teach.darbhanga.teacher.databinding.ActivityLeaderboardBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LeaderboardActivity : AppCompatActivity() {

    private lateinit var b: ActivityLeaderboardBinding
    private val adapter = LbAdapter()

    private var isTeacher = false
    private var myId = ""
    private var myBatch = ""
    private var data: LbData? = null
    private var settings = LbSettings()
    private var batchScope = true
    private var selectedBatch = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLeaderboardBinding.inflate(layoutInflater)
        setContentView(b.root)

        isTeacher = intent.getStringExtra("mode") == "teacher"
        myId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        b.rv.layoutManager = LinearLayoutManager(this)
        b.rv.adapter = adapter

        b.tvBack.setOnClickListener { finish() }

        // Teacher ko default me poori tuition, student ko apna batch
        batchScope = !isTeacher
        b.pBatch.text = if (isTeacher) "Batch wise" else "Mera Batch"
        b.pTuition.text = "Poori Tuition"
        b.pBatch.setOnClickListener { batchScope = true; render() }
        b.pTuition.setOnClickListener { batchScope = false; render() }

        if (isTeacher) {
            b.btnRewards.visibility = View.VISIBLE
            b.btnRefresh.visibility = View.VISIBLE
            b.btnRewards.setOnClickListener {
                startActivity(Intent(this, LeaderboardSettingsActivity::class.java))
            }
            b.btnRefresh.setOnClickListener { refresh() }
        }

        b.actBatch.setOnItemClickListener { parent, _, pos, _ ->
            selectedBatch = parent.getItemAtPosition(pos).toString()
            render()
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                settings = LeaderboardEngine.loadSettings()
                if (!isTeacher && myId.isNotEmpty()) {
                    myBatch = FirebaseFirestore.getInstance().collection("students")
                        .document(myId).get().await().getString("batch") ?: ""
                }
                data = LeaderboardEngine.load()
                render()
            } catch (e: Exception) {
                toast("Error: ${e.localizedMessage}")
            }
        }
    }

    private fun refresh() {
        b.progress.visibility = View.VISIBLE
        b.btnRefresh.isEnabled = false
        lifecycleScope.launch {
            try {
                data = LeaderboardEngine.recompute()
                render()
                toast("Leaderboard update ho gaya")
            } catch (e: Exception) {
                toast("Update fail: ${e.localizedMessage}")
            }
            b.progress.visibility = View.GONE
            b.btnRefresh.isEnabled = true
        }
    }

    private fun styleScope() {
        fun paint(tv: TextView, sel: Boolean) {
            tv.setTextColor(UiHelper.color(this, if (sel) R.color.white else R.color.text_gray))
            tv.backgroundTintList = ColorStateList.valueOf(
                UiHelper.color(this, if (sel) R.color.indigo else R.color.t_gray)
            )
        }
        paint(b.pBatch, batchScope)
        paint(b.pTuition, !batchScope)
    }

    private fun rewardsText(): String {
        if (batchScope) {
            val lines = (1..settings.winners).mapNotNull { r ->
                val t = settings.rewardFor(r)
                if (t.isEmpty()) null else "${LbUi.medal(r)}  Rank $r: $t"
            }
            return if (lines.isEmpty()) "🎁 Batch topper ka inaam abhi teacher ne set nahi kiya"
            else "🎁 Batch Topper Inaam\n" + lines.joinToString("\n")
        }
        return if (settings.tuitionReward.isEmpty()) "🎁 Tuition Topper ka inaam abhi teacher ne set nahi kiya"
        else "🌟 Tuition Topper Inaam\n${settings.tuitionReward}"
    }

    private fun render() {
        styleScope()
        b.tvRewards.text = rewardsText()

        val d = data
        if (d == null || d.entries.isEmpty()) {
            adapter.submit(emptyList(), myId)
            b.tvEmpty.visibility = View.VISIBLE
            b.tvEmpty.text =
                if (isTeacher) "Leaderboard abhi nahi bana.\nUpar ⟳ dabakar banayein."
                else "Leaderboard abhi ban raha hai.\nTeacher ke update karne ke baad dikhega."
            b.tvSub.text = "Abhi data nahi hai"
            b.tvMyRank.text = ""
            b.tilBatch.visibility = View.GONE
            return
        }

        val batches = d.entries.map { it.batch }.filter { it.isNotEmpty() }.distinct().sorted()
        if (isTeacher) {
            b.actBatch.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, batches))
            if (selectedBatch.isEmpty() || selectedBatch !in batches) {
                selectedBatch = batches.firstOrNull() ?: ""
            }
            b.actBatch.setText(selectedBatch, false)
            b.tilBatch.visibility = if (batchScope && batches.isNotEmpty()) View.VISIBLE else View.GONE
        } else {
            selectedBatch = myBatch
            b.tilBatch.visibility = View.GONE
        }

        val ranked =
            if (batchScope) LeaderboardEngine.rank(d.entries, selectedBatch)
            else LeaderboardEngine.rank(d.entries)

        adapter.submit(ranked.take(15), myId)

        val time = if (d.updatedMs > 0)
            SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(d.updatedMs)) else "-"
        b.tvSub.text = "${LbUi.monthLabel(d.month)}  •  Update: $time"

        if (ranked.isEmpty()) {
            b.tvEmpty.visibility = View.VISIBLE
            b.tvEmpty.text = "Is batch me koi student nahi hai"
        } else {
            b.tvEmpty.visibility = View.GONE
        }

        if (isTeacher) {
            b.tvMyRank.text = "Total ${ranked.size} students  •  Top 15 dikh rahe hain"
        } else {
            val me = ranked.firstOrNull { it.e.id == myId }
            b.tvMyRank.text = when {
                me == null -> "Aapki rank abhi nahi bani"
                me.rank <= 15 -> "🎉 Aap top 15 me hain!  Rank #${me.rank} / ${ranked.size}"
                else -> "Aapki rank: #${me.rank} / ${ranked.size}   •   Score ${fmtNum(me.e.score)}"
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}