package com.teach.darbhanga.teacher

import android.graphics.Typeface
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.teach.darbhanga.teacher.databinding.ActivityQuizBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

class QuizActivity : AppCompatActivity() {

    private lateinit var b: ActivityQuizBinding
    private val db = FirebaseFirestore.getInstance()

    private var quizId = ""
    private var quizTitle = ""
    private var studentName = ""
    private var correctList: List<Int> = emptyList()
    private val groups = mutableListOf<RadioGroup>()
    private val optionIds = mutableListOf<List<Int>>()
    private var timer: CountDownTimer? = null
    private var submitted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(b.root)

        quizId = intent.getStringExtra("quizId") ?: ""
        b.btnSubmit.isEnabled = false
        b.btnSubmit.setOnClickListener { confirmSubmit() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                toast("Quiz ke beech me back nahi kar sakte. Submit karein.")
            }
        })

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid == null) {
                    finish()
                    return@launch
                }
                studentName = db.collection("users").document(uid).get().await()
                    .getString("name") ?: ""
                val q = db.collection("quizzes").document(quizId).get().await()
                quizTitle = q.getString("title") ?: ""
                val minutes = q.getLong("minutes") ?: 10L

                @Suppress("UNCHECKED_CAST")
                val questions = q.get("questions") as? List<Map<String, Any?>> ?: emptyList()
                if (questions.isEmpty()) {
                    toast("Is quiz me sawal nahi hain")
                    finish()
                    return@launch
                }
                buildQuiz(questions)
                startTimer(minutes)
                b.btnSubmit.isEnabled = true
            } catch (e: Exception) {
                toast("Error: ${e.localizedMessage}")
                finish()
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildQuiz(questions: List<Map<String, Any?>>) {
        b.tvQuizTitle.text = quizTitle
        correctList = questions.map { (it["correct"] as? Number)?.toInt() ?: -1 }

        questions.forEachIndexed { i, m ->
            val tv = TextView(this)
            tv.text = "Q${i + 1}. ${m["q"]}"
            tv.textSize = 17f
            tv.setTypeface(null, Typeface.BOLD)
            tv.setPadding(0, dp(20), 0, dp(8))

            val rg = RadioGroup(this)
            val opts = (m["options"] as? List<*>)?.map { it.toString() } ?: emptyList()
            val ids = mutableListOf<Int>()
            opts.forEachIndexed { j, o ->
                val rb = RadioButton(this)
                rb.id = View.generateViewId()
                rb.text = "${'A' + j}. $o"
                rb.textSize = 16f
                rg.addView(rb)
                ids.add(rb.id)
            }
            groups.add(rg)
            optionIds.add(ids)
            b.container.addView(tv)
            b.container.addView(rg)
        }
    }

    private fun startTimer(minutes: Long) {
        timer = object : CountDownTimer(minutes * 60_000L, 1000L) {
            override fun onTick(ms: Long) {
                val s = ms / 1000
                b.tvTimer.text = String.format(Locale.US, "Time left: %02d:%02d", s / 60, s % 60)
            }

            override fun onFinish() {
                b.tvTimer.text = "Time khatam"
                submit(true)
            }
        }.start()
    }

    private fun confirmSubmit() {
        val unanswered = groups.count { it.checkedRadioButtonId == -1 }
        val msg = if (unanswered > 0) "$unanswered sawal chhute hain. Phir bhi submit karein?"
        else "Quiz submit karein?"
        AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton("Submit") { _, _ -> submit(false) }
            .setNegativeButton("Wapas", null)
            .show()
    }

    private fun submit(auto: Boolean) {
        if (submitted) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        submitted = true
        timer?.cancel()
        b.btnSubmit.isEnabled = false

        var score = 0
        groups.forEachIndexed { i, rg ->
            val sel = optionIds[i].indexOf(rg.checkedRadioButtonId)
            if (sel >= 0 && sel == correctList[i]) score++
        }
        val total = groups.size

        lifecycleScope.launch {
            try {
                db.collection("quizAttempts").document("${quizId}_$uid").set(
                    mapOf(
                        "quizId" to quizId,
                        "quizTitle" to quizTitle,
                        "studentId" to uid,
                        "name" to studentName,
                        "score" to score.toLong(),
                        "total" to total.toLong(),
                        "createdAt" to Timestamp.now()
                    )
                ).await()
                AlertDialog.Builder(this@QuizActivity)
                    .setTitle(if (auto) "Time khatam" else "Quiz submit ho gaya")
                    .setMessage("Aapka score: $score / $total")
                    .setCancelable(false)
                    .setPositiveButton("OK") { _, _ -> finish() }
                    .show()
            } catch (e: Exception) {
                submitted = false
                b.btnSubmit.isEnabled = true
                toast("Submit fail: ${e.localizedMessage}")
            }
        }
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}