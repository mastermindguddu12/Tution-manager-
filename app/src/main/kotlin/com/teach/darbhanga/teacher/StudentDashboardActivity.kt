package com.teach.darbhanga.teacher

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.teach.darbhanga.teacher.databinding.ActivityStudentDashboardBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StudentDashboardActivity : AppCompatActivity() {

    private lateinit var b: ActivityStudentDashboardBinding
    private val db = FirebaseFirestore.getInstance()
    private var attListener: ListenerRegistration? = null
    private var feeListener: ListenerRegistration? = null
    private var photoUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityStudentDashboardBinding.inflate(layoutInflater)
        setContentView(b.root)

        FcmHelper.setup(this)

        b.tvName.text = intent.getStringExtra("name") ?: ""
        ProfileHelper.load(b.ivAvatar, null)

        b.ivAvatar.setOnClickListener {
            ProfileHelper.promptAndSave(this, photoUrl) {
                photoUrl = it
                ProfileHelper.load(b.ivAvatar, it)
            }
        }

        b.btnLogout.setOnClickListener {
            removeListeners()
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        b.cardLb.setOnClickListener {
            startActivity(Intent(this, LeaderboardActivity::class.java).putExtra("mode", "student"))
        }

        UiHelper.addTile(b.grid, "📊", "Test Results", "Marks aur percentage", R.color.t_blue) {
            startActivity(Intent(this, StudentResultsActivity::class.java))
        }
        UiHelper.addTile(b.grid, "🧠", "Quizzes", "Quiz dein, score dekhein", R.color.t_pink) {
            startActivity(Intent(this, QuizListActivity::class.java).putExtra("mode", "student"))
        }
        UiHelper.addTile(b.grid, "🏆", "Leaderboard", "Rank aur inaam dekhein", R.color.t_orange, span = 2) {
            startActivity(Intent(this, LeaderboardActivity::class.java).putExtra("mode", "student"))
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            b.tvAttDetail.text = "Login nahi mila, dobara login karein"
            return
        }
        loadProfile(uid)
        listenAttendance(uid)
        listenFees(uid)
    }

    override fun onResume() {
        super.onResume()
        loadLeaderboard()
    }

    private fun loadProfile(uid: String) {
        lifecycleScope.launch {
            try {
                val u = db.collection("users").document(uid).get().await()
                u.getString("name")?.let { if (it.isNotEmpty()) b.tvName.text = it }
                photoUrl = u.getString("photoUrl")
                ProfileHelper.load(b.ivAvatar, photoUrl)
            } catch (_: Exception) {
            }
            try {
                val s = db.collection("students").document(uid).get().await()
                val batch = s.getString("batch")
                if (!batch.isNullOrEmpty()) b.tvBatch.text = batch
            } catch (_: Exception) {
            }
        }
    }

    private fun loadLeaderboard() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        lifecycleScope.launch {
            try {
                val settings = LeaderboardEngine.loadSettings()
                val data = LeaderboardEngine.load()
                val batch = db.collection("students").document(uid).get().await()
                    .getString("batch") ?: ""
                renderLb(uid, batch, settings, data)
            } catch (e: Exception) {
                b.llLbBody.visibility = View.GONE
                b.tvLbEmpty.visibility = View.VISIBLE
                b.tvLbEmpty.text = "Leaderboard load nahi hua. Tap karke dekhein."
            }
        }
    }

    private fun renderLb(uid: String, batch: String, s: LbSettings, data: LbData?) {
        if (data == null || data.entries.none { it.score > 0 }) {
            b.llLbBody.visibility = View.GONE
            b.tvLbEmpty.visibility = View.VISIBLE
            b.tvLbEmpty.text = "Leaderboard abhi ban raha hai. Test, quiz aur attendance ke baad rank dikhegi."
            return
        }
        b.tvLbEmpty.visibility = View.GONE
        b.llLbBody.visibility = View.VISIBLE
        b.tvLbMonth.text = LbUi.monthLabel(data.month)

        val batchRanked = LeaderboardEngine.rank(data.entries, batch)
        val all = LeaderboardEngine.rank(data.entries)
        val show = s.winners.coerceIn(1, 3)

        b.tvLbBatchTitle.text = "🏅 Mera Batch ($batch)  •  Top $show"
        b.llLbBatch.removeAllViews()
        batchRanked.take(show).forEach { r ->
            val rw = s.rewardFor(r.rank)
            LbUi.addMini(b.llLbBatch, r, if (rw.isNotEmpty()) "🎁 $rw" else batch)
        }

        b.llLbTuition.removeAllViews()
        all.firstOrNull()?.let { top ->
            val sub = if (s.tuitionReward.isNotEmpty()) "🎁 ${s.tuitionReward}"
            else "${top.e.batch}  •  Tuition Topper"
            LbUi.addMini(b.llLbTuition, top, sub)
        }

        val meB = batchRanked.firstOrNull { it.e.id == uid }
        val meA = all.firstOrNull { it.e.id == uid }
        b.tvLbMine.text =
            "Aapki rank:  Batch #${meB?.rank ?: "-"} / ${batchRanked.size}    •    Tuition #${meA?.rank ?: "-"} / ${all.size}"
    }

    private fun setChip(tv: TextView, text: String, textColor: Int, bgColor: Int) {
        tv.text = text
        tv.setTextColor(UiHelper.color(this, textColor))
        tv.backgroundTintList = ColorStateList.valueOf(UiHelper.color(this, bgColor))
    }

    private fun listenAttendance(uid: String) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        attListener = db.collection("attendance")
            .whereEqualTo("studentId", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    b.tvAttDetail.text = "Attendance error: ${err.localizedMessage}"
                    return@addSnapshotListener
                }
                val docs = snap?.documents ?: emptyList()
                val total = docs.size
                val present = docs.count { it.getString("status") == "present" }
                val pct = if (total == 0) 0 else present * 100 / total

                b.tvAttPct.text = "$pct%"
                b.progAtt.progress = pct
                b.tvAttDetail.text =
                    if (total == 0) "Abhi attendance mark nahi hui"
                    else "Present $present / $total din"

                when (docs.firstOrNull { it.getString("date") == today }?.getString("status")) {
                    "present" -> setChip(b.tvToday, "Aaj: PRESENT", R.color.green, R.color.t_green)
                    "absent" -> setChip(b.tvToday, "Aaj: ABSENT", R.color.red, R.color.t_red)
                    else -> setChip(b.tvToday, "Aaj: mark nahi hui", R.color.text_gray, R.color.t_gray)
                }
            }
    }

    private fun listenFees(uid: String) {
        val month = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

        feeListener = db.collection("fees")
            .whereEqualTo("studentId", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    b.tvFeeDetail.text = "Fee error: ${err.localizedMessage}"
                    return@addSnapshotListener
                }
                val docs = snap?.documents ?: emptyList()
                val thisMonth = docs.firstOrNull { it.getString("month") == month }
                val pending = docs.filter { it.getBoolean("paid") != true }
                val pendingAmt = pending.sumOf { it.getLong("amount") ?: 0L }

                when {
                    thisMonth == null -> {
                        setChip(b.tvFeeChip, "Generate nahi hui", R.color.text_gray, R.color.t_gray)
                        b.tvFeeDetail.text = "Is mahine ($month) ki fee abhi nahi bani"
                    }
                    thisMonth.getBoolean("paid") == true -> {
                        setChip(b.tvFeeChip, "PAID", R.color.green, R.color.t_green)
                        b.tvFeeDetail.text = "Is mahine ($month) ki ₹${thisMonth.getLong("amount") ?: 0} fee jama hai"
                    }
                    else -> {
                        setChip(b.tvFeeChip, "PENDING", R.color.red, R.color.t_red)
                        b.tvFeeDetail.text = "Is mahine ($month) ki ₹${thisMonth.getLong("amount") ?: 0} fee baaki hai"
                    }
                }
                b.tvFeePending.text =
                    if (pending.isEmpty()) "Koi pending fee nahi"
                    else "Kul pending: ₹$pendingAmt (${pending.size} mahine)"
            }
    }

    private fun removeListeners() {
        attListener?.remove()
        feeListener?.remove()
    }

    override fun onDestroy() {
        removeListeners()
        super.onDestroy()
    }
}