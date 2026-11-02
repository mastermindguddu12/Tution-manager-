package com.teach.darbhanga.teacher

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.teach.darbhanga.teacher.databinding.ActivitySimpleListBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TestListActivity : AppCompatActivity() {

    private lateinit var b: ActivitySimpleListBinding
    private val db = FirebaseFirestore.getInstance()
    private val listAdapter = SimpleAdapter { open(it) }
    private var listener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySimpleListBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.tvHeader.text = "Tests"
        b.tvInfo.text = "Test par tap karke marks daalein aur publish karein"
        b.btnAction.visibility = View.VISIBLE
        b.btnAction.text = "Naya Test Banayein"
        b.btnAction.setOnClickListener { showCreate() }

        b.rv.layoutManager = LinearLayoutManager(this)
        b.rv.adapter = listAdapter

        listener = db.collection("tests").addSnapshotListener { snap, err ->
            if (err != null) {
                toast("Error: ${err.localizedMessage}")
                return@addSnapshotListener
            }
            val docs = snap?.documents
                ?.sortedByDescending { it.getTimestamp("createdAt")?.seconds ?: 0L }
                ?: emptyList()
            listAdapter.submit(docs.map { d ->
                val pub = d.getBoolean("published") == true
                SimpleItem(
                    id = d.id,
                    title = d.getString("title") ?: "",
                    sub = "${d.getString("batch") ?: ""}  •  Total ${d.getLong("maxMarks") ?: 0}  •  ${d.getString("date") ?: ""}",
                    status = if (pub) "PUBLISHED" else "DRAFT",
                    statusColor = if (pub) "#2E7D32" else "#EF6C00",
                    extra = mapOf(
                        "batch" to (d.getString("batch") ?: ""),
                        "maxMarks" to (d.getLong("maxMarks") ?: 0L)
                    )
                )
            })
        }
    }

    private fun open(item: SimpleItem) {
        startActivity(
            Intent(this, MarksActivity::class.java)
                .putExtra("testId", item.id)
                .putExtra("title", item.title)
                .putExtra("batch", item.extra["batch"] as? String ?: "")
                .putExtra("maxMarks", item.extra["maxMarks"] as? Long ?: 0L)
        )
    }

    private fun showCreate() {
        lifecycleScope.launch {
            val batches = try {
                Batches.load()
            } catch (e: Exception) {
                toast("Error: ${e.localizedMessage}")
                return@launch
            }
            if (batches.isEmpty()) {
                toast("Pehle student add karein")
                return@launch
            }
            val ctx = this@TestListActivity
            val pad = (16 * resources.displayMetrics.density).toInt()
            val box = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, 0)
            }
            val etTitle = EditText(ctx).apply {
                hint = "Test ka naam (jaise Unit Test 1)"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
            val sp = Spinner(ctx).apply {
                adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, batches)
            }
            val etMax = EditText(ctx).apply {
                hint = "Total marks (jaise 50)"
                inputType = InputType.TYPE_CLASS_NUMBER
            }
            box.addView(etTitle)
            box.addView(sp)
            box.addView(etMax)

            AlertDialog.Builder(ctx)
                .setTitle("Naya Test")
                .setView(box)
                .setPositiveButton("Banayein") { _, _ ->
                    val title = etTitle.text.toString().trim()
                    val max = etMax.text.toString().trim().toLongOrNull()
                    if (title.isEmpty() || max == null || max <= 0) {
                        toast("Naam aur sahi total marks daalein")
                        return@setPositiveButton
                    }
                    db.collection("tests").add(
                        mapOf(
                            "title" to title,
                            "batch" to sp.selectedItem.toString(),
                            "maxMarks" to max,
                            "date" to SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                            "published" to false,
                            "createdAt" to Timestamp.now()
                        )
                    ).addOnFailureListener { toast("Error: ${it.localizedMessage}") }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onDestroy() {
        listener?.remove()
        super.onDestroy()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}