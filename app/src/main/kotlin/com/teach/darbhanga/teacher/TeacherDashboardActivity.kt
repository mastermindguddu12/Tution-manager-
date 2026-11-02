package com.teach.darbhanga.teacher

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.teach.darbhanga.teacher.databinding.ActivityTeacherDashboardBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TeacherDashboardActivity : AppCompatActivity() {

    private lateinit var b: ActivityTeacherDashboardBinding
    private val db = FirebaseFirestore.getInstance()
    private var photoUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityTeacherDashboardBinding.inflate(layoutInflater)
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
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        b.cardLb.setOnClickListener {
            startActivity(Intent(this, LeaderboardActivity::class.java).putExtra("mode", "teacher"))
        }

        UiHelper.addTile(b.grid, "➕", "Naya Student", "Admission add karein", R.color.t_blue) {
            startActivity(Intent(this, AddStudentActivity::class.java))
        }
        UiHelper.addTile(b.grid, "🎓", "Students", "List aur search", R.color.t_purple) {
            startActivity(Intent(this, StudentListActivity::class.java))
        }
        UiHelper.addTile(b.grid, "✅", "Attendance", "Aaj ki hazri", R.color.t_green) {
            startActivity(Intent(this, AttendanceActivity::class.java))
        }
        UiHelper.addTile(b.grid, "💰", "Fees", "Paid, pending, call", R.color.t_orange) {
            startActivity(Intent(this, FeeActivity::class.java))
        }
        UiHelper.addTile(b.grid, "📝", "Tests", "Marks aur result", R.color.t_teal) {
            startActivity(Intent(this, TestListActivity::class.java))
        }
        UiHelper.addTile(b.grid, "🧠", "Quizzes", "MCQ quiz banayein", R.color.t_pink) {
            startActivity(Intent(this, QuizListActivity::class.java).putExtra("mode", "teacher"))
        }
        UiHelper.addTile(b.grid, "🏆", "Leaderboard", "Rank aur inaam set karein", R.color.t_orange) {
            startActivity(Intent(this, LeaderboardActivity::class.java).putExtra("mode", "teacher"))
        }
        UiHelper.addTile(b.grid, "📊", "Reports", "Attendance aur fee report", R.color.t_red) {
            startActivity(Intent(this, ReportsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadProfile()
        loadStats()
        loadLeaderboard()
    }

    private fun loadProfile() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        lifecycleScope.launch {
            try {
                val d = db.collection("users").document(uid).get().await()
                d.getString("name")?.let { if (it.isNotEmpty()) b.tvName.text = it }
                photoUrl = d.getString("photoUrl")
                ProfileHelper.load(b.ivAvatar, photoUrl)
            } catch (_: Exception) {
            }
        }
    }

    private fun loadStats() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val month = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        lifecycleScope.launch {
            try {
                val students = db.collection("students").get().await().size()
                b.tvStatStudents.text = students.toString()
            } catch (_: Exception) {
            }
            try {
                val present = db.collection("attendance")
                    .whereEqualTo("date", today)
                    .whereEqualTo("status", "present")
                    .get().await().size()
                b.tvStatPresent.text = present.toString()
            } catch (_: Exception) {
            }
            try {
                val pending = db.collection("fees")
                    .whereEqualTo("month", month)
                    .whereEqualTo("paid", false)
                    .get().await().size()
                b.tvStatPending.text = pending.toString()
            } catch (_: Exception) {
            }
        }
    }

    private fun loadLeaderboard() {
        lifecycleScope.launch {
            try {
                val settings = LeaderboardEngine.loadSettings()
                var data = LeaderboardEngine.load()
                if (data != null) renderLb(data, settings)

                // 15 minute se purana ya naya mahina ho to teacher ke app se update
                val stale = data == null ||
                    data.month != LeaderboardEngine.currentMonth() ||
                    System.currentTimeMillis() - data.updatedMs > 15 * 60 * 1000L
                if (stale) {
                    data = LeaderboardEngine.recompute()
                    renderLb(data, settings)
                }
            } catch (e: Exception) {
                b.llLbBody.visibility = View.GONE
                b.tvLbEmpty.visibility = View.VISIBLE
                b.tvLbEmpty.text = "Leaderboard load nahi hua. Tap karke dekhein."
            }
        }
    }

    private fun renderLb(d: LbData, s: LbSettings) {
        b.tvLbMonth.text = LbUi.monthLabel(d.month)

        if (d.entries.none { it.score > 0 }) {
            b.llLbBody.visibility = View.GONE
            b.tvLbEmpty.visibility = View.VISIBLE
            b.tvLbEmpty.text = "Abhi kisi ka score nahi bana. Test, quiz aur attendance ke baad rank dikhegi."
            return
        }
        b.tvLbEmpty.visibility = View.GONE
        b.llLbBody.visibility = View.VISIBLE

        b.llLbTuition.removeAllViews()
        LeaderboardEngine.rank(d.entries).firstOrNull()?.let { top ->
            val sub = if (s.tuitionReward.isNotEmpty()) "🎁 ${s.tuitionReward}" else "${top.e.batch}  •  inaam set nahi"
            LbUi.addMini(b.llLbTuition, top, sub)
        }

        b.llLbBatches.removeAllViews()
        val batches = d.entries.map { it.batch }.filter { it.isNotEmpty() }.distinct().sorted()
        batches.take(4).forEach { bt ->
            val top = LeaderboardEngine.rank(d.entries, bt).firstOrNull() ?: return@forEach
            val rw = s.rewardFor(1)
            LbUi.addMini(b.llLbBatches, top, if (rw.isNotEmpty()) "$bt  •  🎁 $rw" else bt)
        }
        if (batches.size > 4) {
            // baaki batch leaderboard screen me dikhenge
        }
    }
}