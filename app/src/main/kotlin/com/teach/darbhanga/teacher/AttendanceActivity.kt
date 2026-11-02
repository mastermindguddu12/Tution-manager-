package com.teach.darbhanga.teacher

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.teach.darbhanga.teacher.databinding.ActivityAttendanceBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AttendanceActivity : AppCompatActivity() {

    private lateinit var b: ActivityAttendanceBinding
    private val db = FirebaseFirestore.getInstance()
    private val adapter = AttendanceAdapter { updateSummary() }

    private var allStudents: List<Student> = emptyList()
    private var selectedBatch: String? = null
    private var date: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAttendanceBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.rv.layoutManager = LinearLayoutManager(this)
        b.rv.adapter = adapter

        b.tvBack.setOnClickListener { finish() }
        b.tvDate.setOnClickListener { pickDate() }
        b.btnSave.setOnClickListener { save() }
        b.btnAllPresent.setOnClickListener { adapter.setAll(true) }
        b.btnAllAbsent.setOnClickListener { adapter.setAll(false) }

        showDate()
        updateSummary()
        loadStudents()
    }

    private fun pretty(d: String): String = try {
        val p = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d)
        SimpleDateFormat("dd MMM yyyy", Locale.US).format(p!!)
    } catch (e: Exception) {
        d
    }

    private fun showDate() {
        b.tvDate.text = "📅  ${pretty(date)}"
    }

    private fun loadStudents() {
        lifecycleScope.launch {
            try {
                val snap = db.collection("students").orderBy("name").get().await()
                allStudents = snap.documents
                    .filter { it.getBoolean("active") != false }
                    .map { d ->
                        Student(
                            id = d.id,
                            name = d.getString("name") ?: "",
                            parentPhone = d.getString("parentPhone") ?: "",
                            batch = d.getString("batch") ?: "",
                            monthlyFee = d.getLong("monthlyFee") ?: 0,
                            photoUrl = d.getString("photoUrl") ?: ""
                        )
                    }
                val batches = allStudents.map { it.batch }.filter { it.isNotEmpty() }.distinct().sorted()
                if (batches.isEmpty()) {
                    b.tvHint.text = "Koi student nahi mila, pehle student add karein"
                    return@launch
                }
                b.actBatch.setAdapter(
                    ArrayAdapter(this@AttendanceActivity, android.R.layout.simple_list_item_1, batches)
                )
                b.actBatch.setOnItemClickListener { _, _, pos, _ ->
                    selectedBatch = batches[pos]
                    loadRows()
                }
                // Ek hi batch ho to apne aap chun lo
                if (batches.size == 1) {
                    b.actBatch.setText(batches[0], false)
                    selectedBatch = batches[0]
                    loadRows()
                }
            } catch (e: Exception) {
                toast("Error: ${e.localizedMessage}")
            }
        }
    }

    private fun loadRows() {
        val batch = selectedBatch ?: return
        lifecycleScope.launch {
            try {
                val students = allStudents.filter { it.batch == batch }
                // Is date ki purani attendance ho to wahi dikhao
                val old = db.collection("attendance")
                    .whereEqualTo("date", date)
                    .whereEqualTo("batch", batch)
                    .get().await()
                val oldStatus = old.documents.associate {
                    (it.getString("studentId") ?: "") to (it.getString("status") ?: "present")
                }
                adapter.submit(students.map { AttRow(it, oldStatus[it.id] != "absent") })
                updateSummary()
            } catch (e: Exception) {
                toast("Error: ${e.localizedMessage}")
            }
        }
    }

    private fun updateSummary() {
        val total = adapter.items.size
        val absent = adapter.items.count { !it.present }
        b.tvTotal.text = "Total $total"
        b.tvPresentCount.text = "Present ${total - absent}"
        b.tvAbsentCount.text = "Absent $absent"
        b.tvHint.text =
            if (selectedBatch == null) "Upar se batch chunein"
            else "Absent karne ke liye student ke card par tap karein"
    }

    private fun pickDate() {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            date = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
            showDate()
            loadRows()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun save() {
        val batch = selectedBatch
        if (batch == null || adapter.items.isEmpty()) {
            toast("Pehle batch chunein")
            return
        }
        setBusy(true)
        lifecycleScope.launch {
            try {
                val wb = db.batch()
                adapter.items.forEach { row ->
                    val s = row.student
                    wb.set(
                        db.collection("attendance").document("${date}_${s.id}"),
                        mapOf(
                            "studentId" to s.id,
                            "name" to s.name,
                            "batch" to batch,
                            "date" to date,
                            "status" to if (row.present) "present" else "absent",
                            "markedAt" to Timestamp.now()
                        )
                    )
                }
                wb.commit().await()

                val absentRows = adapter.items.filter { !it.present }
                if (absentRows.isEmpty()) {
                    toast("Attendance save ho gayi. Sab present hain")
                } else {
                    val ids = JSONArray()
                    absentRows.forEach { ids.put(it.student.id) }
                    val res = GasApi.call(
                        JSONObject()
                            .put("action", "notifyAbsent")
                            .put("date", date)
                            .put("studentIds", ids)
                    )
                    if (res.optBoolean("ok")) {
                        toast("Save ho gayi. Absent: ${absentRows.size}, notification gayi: ${res.optInt("sent")}")
                    } else {
                        toast("Save ho gayi, par notification nahi gayi: ${res.optString("error")}")
                    }
                }
            } catch (e: Exception) {
                toast("Save fail: ${e.localizedMessage}")
            }
            setBusy(false)
        }
    }

    private fun setBusy(busy: Boolean) {
        b.progress.visibility = if (busy) View.VISIBLE else View.GONE
        b.btnSave.isEnabled = !busy
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}