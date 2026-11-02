package com.teach.darbhanga.teacher

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.teach.darbhanga.teacher.databinding.ActivityFeeBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class FeeActivity : AppCompatActivity() {

    private lateinit var b: ActivityFeeBinding
    private val db = FirebaseFirestore.getInstance()
    private val adapter = FeeAdapter { showActions(it) }

    private var month = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    private var filter = "pending"
    private var all: List<FeeRow> = emptyList()
    private var photos: Map<String, String> = emptyMap()
    private var listener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityFeeBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.rv.layoutManager = LinearLayoutManager(this)
        b.rv.adapter = adapter

        b.tvBack.setOnClickListener { finish() }
        b.tvMonth.setOnClickListener { pickMonth() }
        b.btnGenerate.setOnClickListener { generate() }
        b.btnRemindAll.setOnClickListener { confirmRemindAll() }
        b.fPending.setOnClickListener { filter = "pending"; applyFilter() }
        b.fPaid.setOnClickListener { filter = "paid"; applyFilter() }
        b.fAll.setOnClickListener { filter = "all"; applyFilter() }

        loadPhotos()
        listen()
    }

    private fun loadPhotos() {
        lifecycleScope.launch {
            try {
                photos = db.collection("students").get().await().documents
                    .associate { it.id to (it.getString("photoUrl") ?: "") }
                applyFilter()
            } catch (_: Exception) {
            }
        }
    }

    private fun listen() {
        listener?.remove()
        b.tvMonth.text = "📅  $month"
        listener = db.collection("fees")
            .whereEqualTo("month", month)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    toast("Error: ${err.localizedMessage}")
                    return@addSnapshotListener
                }
                all = snap?.documents?.map { d ->
                    FeeRow(
                        id = d.id,
                        studentId = d.getString("studentId") ?: "",
                        name = d.getString("name") ?: "",
                        batch = d.getString("batch") ?: "",
                        parentPhone = d.getString("parentPhone") ?: "",
                        month = d.getString("month") ?: "",
                        amount = d.getLong("amount") ?: 0,
                        paid = d.getBoolean("paid") ?: false
                    )
                }?.sortedBy { it.name.lowercase() } ?: emptyList()
                applyFilter()
            }
    }

    private fun styleFilter(tv: TextView, selected: Boolean) {
        tv.setTextColor(UiHelper.color(this, if (selected) R.color.white else R.color.text_gray))
        tv.backgroundTintList = ColorStateList.valueOf(
            UiHelper.color(this, if (selected) R.color.indigo else R.color.t_gray)
        )
    }

    private fun applyFilter() {
        styleFilter(b.fPending, filter == "pending")
        styleFilter(b.fPaid, filter == "paid")
        styleFilter(b.fAll, filter == "all")

        val list = when (filter) {
            "paid" -> all.filter { it.paid }
            "pending" -> all.filter { !it.paid }
            else -> all
        }.map { it.copy(photoUrl = photos[it.studentId] ?: "") }
        adapter.submit(list)

        val total = all.sumOf { it.amount }
        val collected = all.filter { it.paid }.sumOf { it.amount }
        val pct = if (total > 0) (collected * 100 / total).toInt() else 0
        val paidCount = all.count { it.paid }
        val pendingCount = all.count { !it.paid }

        b.tvCollected.text = "₹$collected"
        b.tvPending.text = "₹${total - collected}"
        b.progFee.progress = pct
        b.tvFeeSummary.text =
            if (all.isEmpty()) "Is mahine ki fee abhi generate nahi hui"
            else "$pct% collect hua  •  Paid $paidCount  •  Pending $pendingCount  •  Total ₹$total"

        if (list.isEmpty()) {
            b.tvEmpty.visibility = View.VISIBLE
            b.tvEmpty.text =
                if (all.isEmpty()) "Is mahine ($month) ki fee generate nahi hui.\nUpar 'Fee Generate' dabayein."
                else "Is filter me koi student nahi hai"
        } else {
            b.tvEmpty.visibility = View.GONE
        }
    }

    private fun pickMonth() {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, _ ->
            month = String.format(Locale.US, "%04d-%02d", y, m + 1)
            listen()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun generate() {
        b.btnGenerate.isEnabled = false
        lifecycleScope.launch {
            try {
                val students = db.collection("students").get().await()
                val existing = db.collection("fees").whereEqualTo("month", month).get().await()
                    .documents.map { it.getString("studentId") }.toSet()

                val wb = db.batch()
                var n = 0
                students.documents.forEach { d ->
                    if (d.getBoolean("active") == false) return@forEach
                    if (d.id in existing) return@forEach
                    wb.set(
                        db.collection("fees").document("${d.id}_$month"),
                        mapOf(
                            "studentId" to d.id,
                            "name" to (d.getString("name") ?: ""),
                            "batch" to (d.getString("batch") ?: ""),
                            "parentPhone" to (d.getString("parentPhone") ?: ""),
                            "month" to month,
                            "amount" to (d.getLong("monthlyFee") ?: 0L),
                            "paid" to false,
                            "paidAt" to null
                        )
                    )
                    n++
                }
                if (n > 0) wb.commit().await()
                toast(if (n > 0) "$n students ki fee generate hui" else "Sabki fee pehle se bani hui hai")
            } catch (e: Exception) {
                toast("Error: ${e.localizedMessage}")
            }
            b.btnGenerate.isEnabled = true
        }
    }

    // ---------- Card tap: options ----------
    private fun showActions(row: FeeRow) {
        val options = if (row.paid) {
            arrayOf("↩  Pending mark karein")
        } else {
            arrayOf("✅  Paid mark karein", "🔔  Student ko fee notification", "📞  Parent ko AI call")
        }
        AlertDialog.Builder(this)
            .setTitle("${row.name}  •  ₹${row.amount}")
            .setItems(options) { _, i ->
                if (row.paid) {
                    setPaid(row, false)
                } else when (i) {
                    0 -> setPaid(row, true)
                    1 -> notifyStudent(row)
                    2 -> confirmCall(row)
                }
            }
            .setNegativeButton("Band karein", null)
            .show()
    }

    private fun setPaid(row: FeeRow, paid: Boolean) {
        db.collection("fees").document(row.id).update(
            mapOf(
                "paid" to paid,
                "paidAt" to if (paid) Timestamp.now() else null
            )
        ).addOnFailureListener { toast("Error: ${it.localizedMessage}") }
    }

    private fun notifyStudent(row: FeeRow) {
        lifecycleScope.launch {
            val res = GasApi.call(
                JSONObject()
                    .put("action", "notifyFee")
                    .put("studentId", row.studentId)
                    .put("amount", row.amount)
                    .put("month", row.month)
            )
            toast(if (res.optBoolean("ok")) "${row.name} ko notification bhej di" else "Fail: ${res.optString("error")}")
        }
    }

    private fun confirmCall(row: FeeRow) {
        AlertDialog.Builder(this)
            .setTitle("AI Call")
            .setMessage("${row.name} ke parent (${row.parentPhone}) ko fee reminder ki AI call lagayein?\n\nCall me paise lagte hain.")
            .setPositiveButton("Call karein") { _, _ ->
                lifecycleScope.launch {
                    toast("Call lag rahi hai...")
                    val res = GasApi.call(
                        JSONObject()
                            .put("action", "callParent")
                            .put("parentPhone", row.parentPhone)
                            .put("studentName", row.name)
                            .put("amount", row.amount)
                            .put("month", row.month)
                    )
                    toast(if (res.optBoolean("ok")) "Call dispatch ho gayi" else "Call fail: ${res.optString("error")}")
                }
            }
            .setNegativeButton("Nahi", null)
            .show()
    }

    private fun confirmRemindAll() {
        val pending = all.count { !it.paid }
        if (pending == 0) {
            toast("Koi pending fee nahi hai")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Fee Reminder")
            .setMessage("$pending pending students ko notification bhejein?")
            .setPositiveButton("Haan") { _, _ ->
                lifecycleScope.launch {
                    val res = GasApi.call(
                        JSONObject().put("action", "remindAllPending").put("month", month)
                    )
                    toast(
                        if (res.optBoolean("ok"))
                            "Notification gayi: ${res.optInt("sent")} / ${res.optInt("total")}"
                        else "Fail: ${res.optString("error")}"
                    )
                }
            }
            .setNegativeButton("Nahi", null)
            .show()
    }

    override fun onDestroy() {
        listener?.remove()
        super.onDestroy()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}