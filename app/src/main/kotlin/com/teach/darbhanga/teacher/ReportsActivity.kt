package com.teach.darbhanga.teacher

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import com.teach.darbhanga.teacher.databinding.ActivityReportsBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReportsActivity : AppCompatActivity() {

    companion object {
        const val ALL = "Sabhi batch"
    }

    private data class AttLine(val name: String, val p: Int, val a: Int) {
        val total get() = p + a
        val pct get() = if (total == 0) 0 else p * 100 / total
    }

    private lateinit var b: ActivityReportsBinding
    private val db = FirebaseFirestore.getInstance()
    private var month = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    private var batch = ALL
    private var lastReport = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(b.root)

        showMonth()
        b.tvMonth.setOnClickListener { pickMonth() }
        b.btnAttReport.setOnClickListener { attendanceReport() }
        b.btnFeeReport.setOnClickListener { feeReport() }
        b.btnShare.setOnClickListener { share() }

        loadBatches()
    }

    private fun loadBatches() {
        lifecycleScope.launch {
            try {
                val list = listOf(ALL) + Batches.load()
                b.actBatch.setAdapter(
                    ArrayAdapter(this@ReportsActivity, android.R.layout.simple_list_item_1, list)
                )
                b.actBatch.setText(ALL, false)
                b.actBatch.setOnItemClickListener { _, _, pos, _ -> batch = list[pos] }
            } catch (e: Exception) {
                toast("Error: ${e.localizedMessage}")
            }
        }
    }

    private fun showMonth() {
        b.tvMonth.text = "Mahina: $month  (badalne ke liye tap karein)"
    }

    private fun pickMonth() {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, _ ->
            month = String.format(Locale.US, "%04d-%02d", y, m + 1)
            showMonth()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun nextMonth(ym: String): String {
        val y = ym.substring(0, 4).toInt()
        val m = ym.substring(5, 7).toInt()
        return if (m == 12) String.format(Locale.US, "%04d-01", y + 1)
        else String.format(Locale.US, "%04d-%02d", y, m + 1)
    }

    private fun attendanceReport() {
        setBusy(true)
        lifecycleScope.launch {
            try {
                val students = db.collection("students").get().await().documents
                    .filter {
                        (batch == ALL || it.getString("batch") == batch) &&
                            it.getBoolean("active") != false
                    }
                    .map { Pair(it.getString("name") ?: "", it.id) }
                    .sortedBy { it.first.lowercase() }

                val att = db.collection("attendance")
                    .whereGreaterThanOrEqualTo("date", "$month-01")
                    .whereLessThan("date", "${nextMonth(month)}-01")
                    .get().await().documents

                val present = HashMap<String, Int>()
                val absent = HashMap<String, Int>()
                for (d in att) {
                    val id = d.getString("studentId") ?: continue
                    if (d.getString("status") == "present") {
                        present[id] = (present[id] ?: 0) + 1
                    } else {
                        absent[id] = (absent[id] ?: 0) + 1
                    }
                }

                val rows = students.map { AttLine(it.first, present[it.second] ?: 0, absent[it.second] ?: 0) }
                val withData = rows.filter { it.total > 0 }.sortedBy { it.pct }
                val noData = rows.filter { it.total == 0 }
                val avg = if (withData.isEmpty()) 0 else withData.sumOf { it.pct } / withData.size

                val sb = StringBuilder()
                sb.append("ATTENDANCE REPORT\n")
                sb.append("Mahina: $month\n")
                sb.append("Batch: $batch\n")
                sb.append("Students: ${rows.size}   Average: $avg%\n\n")
                withData.forEachIndexed { i, r ->
                    sb.append("${i + 1}. ${r.name}: ${r.pct}% (P ${r.p}, A ${r.a})")
                    if (r.pct < 75) sb.append("  ⚠ kam")
                    sb.append("\n")
                }
                if (noData.isNotEmpty()) {
                    sb.append("\nIs mahine record nahi mila:\n")
                    noData.forEach { sb.append("- ${it.name}\n") }
                }
                if (rows.isEmpty()) sb.append("Is batch me koi student nahi hai")
                show(sb.toString())
            } catch (e: Exception) {
                toast("Error: ${e.localizedMessage}")
            } finally {
                setBusy(false)
            }
        }
    }

    private fun feeReport() {
        setBusy(true)
        lifecycleScope.launch {
            try {
                val docs = db.collection("fees").whereEqualTo("month", month)
                    .get().await().documents
                    .filter { batch == ALL || it.getString("batch") == batch }

                if (docs.isEmpty()) {
                    show("Is mahine ($month) ki fee generate nahi hui.\nPehle Fee Management me fee generate karein.")
                    return@launch
                }

                val total = docs.sumOf { it.getLong("amount") ?: 0L }
                val paidDocs = docs.filter { it.getBoolean("paid") == true }
                val pendingDocs = docs.filter { it.getBoolean("paid") != true }
                    .sortedBy { (it.getString("name") ?: "").lowercase() }
                val collected = paidDocs.sumOf { it.getLong("amount") ?: 0L }
                val pct = if (total > 0) collected * 100 / total else 0L

                val sb = StringBuilder()
                sb.append("FEE REPORT\n")
                sb.append("Mahina: $month\n")
                sb.append("Batch: $batch\n\n")
                sb.append("Total: ₹$total\n")
                sb.append("Collected: ₹$collected ($pct%)\n")
                sb.append("Pending: ₹${total - collected}\n")
                sb.append("Paid: ${paidDocs.size}   Pending: ${pendingDocs.size}\n")
                if (pendingDocs.isNotEmpty()) {
                    sb.append("\nPending list:\n")
                    pendingDocs.forEachIndexed { i, d ->
                        sb.append("${i + 1}. ${d.getString("name") ?: ""}: ₹${d.getLong("amount") ?: 0}\n")
                    }
                }
                show(sb.toString())
            } catch (e: Exception) {
                toast("Error: ${e.localizedMessage}")
            } finally {
                setBusy(false)
            }
        }
    }

    private fun show(text: String) {
        lastReport = text
        b.tvReport.text = text
    }

    private fun share() {
        if (lastReport.isEmpty()) {
            toast("Pehle report banayein")
            return
        }
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, lastReport)
        }
        startActivity(Intent.createChooser(i, "Report share karein"))
    }

    private fun setBusy(busy: Boolean) {
        b.btnAttReport.isEnabled = !busy
        b.btnFeeReport.isEnabled = !busy
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}