package com.teach.darbhanga.teacher

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.teach.darbhanga.teacher.databinding.ActivityQuizCreateBinding
import com.teach.darbhanga.teacher.databinding.ItemQuestionEditBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class QuizCreateActivity : AppCompatActivity() {

    private lateinit var b: ActivityQuizCreateBinding
    private val db = FirebaseFirestore.getInstance()
    private val qs = mutableListOf<ItemQuestionEditBinding>()
    private var batches: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityQuizCreateBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnAddQ.setOnClickListener { addQuestion() }
        b.btnSaveQuiz.setOnClickListener { save() }
        addQuestion()

        lifecycleScope.launch {
            try {
                batches = Batches.load()
                if (batches.isEmpty()) {
                    toast("Pehle student add karein")
                    return@launch
                }
                b.spBatch.adapter = ArrayAdapter(
                    this@QuizCreateActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    batches
                )
            } catch (e: Exception) {
                toast("Error: ${e.localizedMessage}")
            }
        }
    }

    private fun addQuestion() {
        val qb = ItemQuestionEditBinding.inflate(layoutInflater, b.llQuestions, false)
        qs.add(qb)
        b.llQuestions.addView(qb.root)
        qb.btnRemove.setOnClickListener {
            if (qs.size <= 1) {
                toast("Kam se kam 1 sawal chahiye")
                return@setOnClickListener
            }
            qs.remove(qb)
            b.llQuestions.removeView(qb.root)
            renumber()
        }
        renumber()
    }

    private fun renumber() {
        qs.forEachIndexed { i, q -> q.tvNo.text = "Sawal ${i + 1}" }
    }

    private fun save() {
        val title = b.etTitle.text.toString().trim()
        val minutes = b.etMinutes.text.toString().trim().toLongOrNull()
        if (title.isEmpty()) return toast("Quiz ka naam daalein")
        if (batches.isEmpty()) return toast("Batch nahi mila")
        if (minutes == null || minutes <= 0) return toast("Timer minute me daalein")

        val list = mutableListOf<Map<String, Any>>()
        for ((i, q) in qs.withIndex()) {
            val text = q.etQ.text.toString().trim()
            val opts = listOf(q.etA, q.etB, q.etC, q.etD).map { it.text.toString().trim() }
            val correct = when {
                q.rbA.isChecked -> 0
                q.rbB.isChecked -> 1
                q.rbC.isChecked -> 2
                q.rbD.isChecked -> 3
                else -> -1
            }
            if (text.isEmpty() || opts.any { it.isEmpty() }) {
                return toast("Sawal ${i + 1}: sawal aur chaaron option bharein")
            }
            if (correct < 0) return toast("Sawal ${i + 1}: sahi jawab chunein")
            list.add(mapOf("q" to text, "options" to opts, "correct" to correct.toLong()))
        }

        b.btnSaveQuiz.isEnabled = false
        lifecycleScope.launch {
            try {
                db.collection("quizzes").add(
                    mapOf(
                        "title" to title,
                        "batch" to b.spBatch.selectedItem.toString(),
                        "minutes" to minutes,
                        "questions" to list,
                        "count" to list.size.toLong(),
                        "createdAt" to Timestamp.now()
                    )
                ).await()
                toast("Quiz save ho gaya")
                finish()
            } catch (e: Exception) {
                b.btnSaveQuiz.isEnabled = true
                toast("Error: ${e.localizedMessage}")
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}