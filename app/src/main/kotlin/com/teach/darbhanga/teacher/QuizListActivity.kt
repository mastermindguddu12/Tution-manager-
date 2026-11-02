package com.teach.darbhanga.teacher

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.teach.darbhanga.teacher.databinding.ActivitySimpleListBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class QuizListActivity : AppCompatActivity() {

    private lateinit var b: ActivitySimpleListBinding
    private val db = FirebaseFirestore.getInstance()
    private val listAdapter = SimpleAdapter { onItem(it) }
    private var teacher = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySimpleListBinding.inflate(layoutInflater)
        setContentView(b.root)

        teacher = intent.getStringExtra("mode") == "teacher"

        b.rv.layoutManager = LinearLayoutManager(this)
        b.rv.adapter = listAdapter

        if (teacher) {
            b.tvHeader.text = "Quizzes"
            b.tvInfo.text = "Quiz par tap karke students ke scores dekhein"
            b.btnAction.visibility = View.VISIBLE
            b.btnAction.text = "Naya Quiz Banayein"
            b.btnAction.setOnClickListener {
                startActivity(Intent(this, QuizCreateActivity::class.java))
            }
        } else {
            b.tvHeader.text = "Mere Quizzes"
            b.tvInfo.text = "Quiz sirf ek baar de sakte hain"
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                if (teacher) {
                    val docs = db.collection("quizzes").get().await().documents
                        .sortedByDescending { it.getTimestamp("createdAt")?.seconds ?: 0L }
                    listAdapter.submit(docs.map { toItem(it, "", "#455A64") })
                } else {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    if (uid == null) {
                        toast("Login nahi mila")
                        return@launch
                    }
                    val batch = db.collection("students").document(uid).get().await()
                        .getString("batch") ?: ""
                    val docs = db.collection("quizzes").whereEqualTo("batch", batch)
                        .get().await().documents
                        .sortedByDescending { it.getTimestamp("createdAt")?.seconds ?: 0L }
                    val attempts = db.collection("quizAttempts").whereEqualTo("studentId", uid)
                        .get().await().documents
                        .associate {
                            (it.getString("quizId") ?: "") to
                                "${it.getLong("score") ?: 0}/${it.getLong("total") ?: 0}"
                        }
                    if (docs.isEmpty()) b.tvInfo.text = "Abhi aapke batch ka koi quiz nahi hai"
                    listAdapter.submit(docs.map { d ->
                        val done = attempts[d.id]
                        toItem(d, done ?: "NEW", if (done != null) "#2E7D32" else "#1565C0")
                    })
                }
            } catch (e: Exception) {
                toast("Error: ${e.localizedMessage}")
            }
        }
    }

    private fun toItem(d: DocumentSnapshot, status: String, color: String) = SimpleItem(
        id = d.id,
        title = d.getString("title") ?: "",
        sub = "${d.getString("batch") ?: ""}  •  ${d.getLong("count") ?: 0} sawal  •  ${d.getLong("minutes") ?: 0} min",
        status = status,
        statusColor = color,
        extra = mapOf("minutes" to (d.getLong("minutes") ?: 0L))
    )

    private fun onItem(item: SimpleItem) {
        if (teacher) showAttempts(item) else startOrShow(item)
    }

    private fun showAttempts(item: SimpleItem) {
        lifecycleScope.launch {
            try {
                val docs = db.collection("quizAttempts").whereEqualTo("quizId", item.id)
                    .get().await().documents
                    .sortedByDescending { it.getLong("score") ?: 0L }
                val msg = if (docs.isEmpty()) "Abhi kisi ne attend nahi kiya"
                else docs.joinToString("\n") {
                    "${it.getString("name") ?: ""}:  ${it.getLong("score") ?: 0} / ${it.getLong("total") ?: 0}"
                }
                AlertDialog.Builder(this@QuizListActivity)
                    .setTitle(item.title)
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .show()
            } catch (e: Exception) {
                toast("Error: ${e.localizedMessage}")
            }
        }
    }

    private fun startOrShow(item: SimpleItem) {
        if (item.status != "NEW") {
            AlertDialog.Builder(this)
                .setTitle(item.title)
                .setMessage("Aap ye quiz de chuke hain.\nAapka score: ${item.status}")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val minutes = item.extra["minutes"] as? Long ?: 0L
        AlertDialog.Builder(this)
            .setTitle(item.title)
            .setMessage("Timer: $minutes minute.\nQuiz sirf ek baar de sakte hain. Start karein?")
            .setPositiveButton("Start") { _, _ ->
                startActivity(Intent(this, QuizActivity::class.java).putExtra("quizId", item.id))
            }
            .setNegativeButton("Abhi nahi", null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}