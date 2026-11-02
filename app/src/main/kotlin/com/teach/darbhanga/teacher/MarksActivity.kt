package com.teach.darbhanga.teacher

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.teach.darbhanga.teacher.databinding.ActivityMarksBinding
import com.teach.darbhanga.teacher.databinding.ItemMarksRowBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

class MarksActivity : AppCompatActivity() {

    private lateinit var b: ActivityMarksBinding
    private val db = FirebaseFirestore.getInstance()

    private var testId = ""
    private var title = ""
    private var batch = ""
    private var maxMarks = 0L
    private var testPublished = false
    private val rows = mutableListOf<Pair<Student, EditText>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMarksBinding.inflate(layoutInflater)
        setContentView(b.root)

        testId = intent.getStringExtra("testId") ?: ""
        title = intent.getStringExtra("title") ?: ""
        batch = intent.getStringExtra("batch") ?: ""
        maxMarks = intent.getLongExtra("maxMarks", 0L)

        b.tvHeader.text = "$title\n$batch  •  Total marks: $maxMarks"
        b.btnSave.setOnClickListener { save(false) }
        b.btnPublish.setOnClickListener { save(true) }

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                val testDoc = db.collection("tests").document(testId).get().await()
                testPublished = testDoc.getBoolean("published") == true

                val students = db.collection("students").whereEqualTo("batch", batch)
                    .get().await().documents
                    .filter { it.getBoolean("active") != false }
                    .map {
                        Student(
                            id = it.id,
                            name = it.getString("name") ?: "",
                            batch = batch,
                            photoUrl = it.getString("photoUrl") ?: ""
                        )
                    }
                    .sortedBy { it.name.lowercase() }

                val old = db.collection("results").whereEqualTo("testId", testId)
                    .get().await().documents
                    .associate { (it.getString("studentId") ?: "") to (it.getDouble("marks") ?: 0.0) }

                buildRows(students, old)
            } catch (e: Exception) {
                toast("Error: ${e.localizedMessage}")
            }
        }
    }

    private fun buildRows(students: List<Student>, old: Map<String, Double>) {
        b.llRows.removeAllViews()
        rows.clear()

        if (students.isEmpty()) {
            toast("Is batch me koi student nahi hai")
            return
        }

        students.forEach { s ->
            val row = ItemMarksRowBinding.inflate(layoutInflater, b.llRows, false)
            row.tvName.text = s.name
            row.etMarks.hint = "/$maxMarks"
            ProfileHelper.load(row.ivAvatar, s.photoUrl)
            old[s.id]?.let { row.etMarks.setText(fmtNum(it)) }
            b.llRows.addView(row.root)
            rows.add(Pair(s, row.etMarks))
        }
    }

    private fun save(publish: Boolean) {
        val pub = publish || testPublished
        val firstPublish = publish && !testPublished
        val wb = db.batch()
        val ids = JSONArray()
        var count = 0

        for ((s, et) in rows) {
            val t = et.text.toString().trim()
            if (t.isEmpty()) continue
            val m = t.toDoubleOrNull()
            if (m == null || m < 0 || m > maxMarks) {
                toast("${s.name}: marks 0 se $maxMarks ke beech hone chahiye")
                return
            }
            wb.set(
                db.collection("results").document("${testId}_${s.id}"),
                mapOf(
                    "testId" to testId,
                    "testTitle" to title,
                    "studentId" to s.id,
                    "name" to s.name,
                    "batch" to batch,
                    "marks" to m,
                    "maxMarks" to maxMarks,
                    "published" to pub,
                    "savedAt" to Timestamp.now()
                )
            )
            ids.put(s.id)
            count++
        }

        if (count == 0) {
            toast("Kam se kam ek student ke marks daalein")
            return
        }
        if (publish) {
            wb.update(db.collection("tests").document(testId), "published", true)
        }

        b.btnSave.isEnabled = false
        b.btnPublish.isEnabled = false
        lifecycleScope.launch {
            try {
                wb.commit().await()
                testPublished = pub

                var msg = if (publish) "Result publish ho gaya ($count students)"
                else "Marks save ho gaye ($count students)"

                if (firstPublish) {
                    val res = GasApi.call(
                        JSONObject()
                            .put("action", "notifyResult")
                            .put("title", title)
                            .put("studentIds", ids)
                    )
                    msg += if (res.optBoolean("ok"))
                        "\nNotification gayi: ${res.optInt("sent")} / ${res.optInt("total")}"
                    else "\nNotification nahi gayi: ${res.optString("error")}"
                }
                toast(msg)
            } catch (e: Exception) {
                toast("Error: ${e.localizedMessage}")
            }
            b.btnSave.isEnabled = true
            b.btnPublish.isEnabled = true
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}