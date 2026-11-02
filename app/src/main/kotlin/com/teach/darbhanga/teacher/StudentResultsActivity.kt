package com.teach.darbhanga.teacher

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.teach.darbhanga.teacher.databinding.ActivitySimpleListBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

class StudentResultsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySimpleListBinding
    private val listAdapter = SimpleAdapter { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySimpleListBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.tvHeader.text = "Mere Test Results"
        b.tvInfo.text = "Load ho raha hai..."
        b.rv.layoutManager = LinearLayoutManager(this)
        b.rv.adapter = listAdapter

        lifecycleScope.launch {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid == null) {
                    b.tvInfo.text = "Login nahi mila"
                    return@launch
                }
                val docs = FirebaseFirestore.getInstance().collection("results")
                    .whereEqualTo("studentId", uid)
                    .whereEqualTo("published", true)
                    .get().await().documents
                    .sortedByDescending { it.getTimestamp("savedAt")?.seconds ?: 0L }

                if (docs.isEmpty()) {
                    b.tvInfo.text = "Abhi koi result publish nahi hua"
                    return@launch
                }

                var sumPct = 0.0
                val items = docs.map { d ->
                    val m = d.getDouble("marks") ?: 0.0
                    val max = (d.getLong("maxMarks") ?: 1L).toDouble()
                    val pct = if (max > 0) m * 100 / max else 0.0
                    sumPct += pct
                    SimpleItem(
                        id = d.id,
                        title = d.getString("testTitle") ?: "",
                        sub = String.format(Locale.US, "%.0f%%", pct),
                        status = "${fmtNum(m)} / ${fmtNum(max)}",
                        statusColor = when {
                            pct >= 60 -> "#2E7D32"
                            pct >= 40 -> "#EF6C00"
                            else -> "#C62828"
                        }
                    )
                }
                b.tvInfo.text = String.format(
                    Locale.US, "Total tests: %d   Average: %.0f%%", items.size, sumPct / items.size
                )
                listAdapter.submit(items)
            } catch (e: Exception) {
                b.tvInfo.text = "Error: ${e.localizedMessage}"
                Toast.makeText(this@StudentResultsActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}