package com.teach.darbhanga.teacher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.teach.darbhanga.teacher.databinding.ActivityStudentListBinding

class StudentListActivity : AppCompatActivity() {

    private lateinit var b: ActivityStudentListBinding
    private val db = FirebaseFirestore.getInstance()
    private val adapter = StudentAdapter { showOptions(it) }
    private var all: List<Student> = emptyList()
    private var listener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityStudentListBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.rv.layoutManager = LinearLayoutManager(this)
        b.rv.adapter = adapter

        b.tvBack.setOnClickListener { finish() }
        b.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddStudentActivity::class.java))
        }
        b.etSearch.doAfterTextChanged { applyFilter() }

        listener = db.collection("students")
            .orderBy("name")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    b.tvCount.text = "Error: ${err.localizedMessage}"
                    return@addSnapshotListener
                }
                all = snap?.documents?.map { d ->
                    Student(
                        id = d.id,
                        name = d.getString("name") ?: "",
                        email = d.getString("email") ?: "",
                        phone = d.getString("phone") ?: "",
                        parentPhone = d.getString("parentPhone") ?: "",
                        batch = d.getString("batch") ?: "",
                        monthlyFee = d.getLong("monthlyFee") ?: 0,
                        photoUrl = d.getString("photoUrl") ?: ""
                    )
                } ?: emptyList()
                applyFilter()
            }
    }

    private fun applyFilter() {
        val q = b.etSearch.text.toString().trim().lowercase()
        val list = if (q.isEmpty()) all else all.filter {
            it.name.lowercase().contains(q) ||
                it.batch.lowercase().contains(q) ||
                it.phone.contains(q) ||
                it.parentPhone.contains(q)
        }
        adapter.submit(list)
        b.tvCount.text =
            if (q.isEmpty()) "Total ${all.size} students"
            else "${list.size} mile (kul ${all.size})"
        b.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showOptions(s: Student) {
        val labels = mutableListOf("📷  Photo badlein", "📞  Parent ko call karein")
        if (s.phone.isNotEmpty()) labels.add("📱  Student ko call karein")

        AlertDialog.Builder(this)
            .setTitle("${s.name}  •  ${s.batch}")
            .setItems(labels.toTypedArray()) { _, i ->
                when (i) {
                    0 -> editPhoto(s)
                    1 -> dial(s.parentPhone)
                    2 -> dial(s.phone)
                }
            }
            .setNegativeButton("Band karein", null)
            .show()
    }

    private fun dial(number: String) {
        if (number.isEmpty()) {
            toast("Number nahi hai")
            return
        }
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    }

    private fun editPhoto(s: Student) {
        val et = EditText(this)
        et.hint = "Photo ka URL (https://...)"
        et.setText(s.photoUrl)
        et.setSingleLine(true)

        val pad = UiHelper.dp(this, 20)
        val box = FrameLayout(this)
        box.setPadding(pad, UiHelper.dp(this, 8), pad, 0)
        box.addView(et)

        AlertDialog.Builder(this)
            .setTitle("${s.name} ki photo")
            .setMessage("Direct image link ya Google Drive link paste karein. Khali chhodne par photo hat jayegi.")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val u = et.text.toString().trim()
                if (u.isNotEmpty() && !u.startsWith("http")) {
                    toast("Link http ya https se shuru hona chahiye")
                    return@setPositiveButton
                }
                val wb = db.batch()
                wb.update(db.collection("students").document(s.id), "photoUrl", u)
                wb.update(db.collection("users").document(s.id), "photoUrl", u)
                wb.commit()
                    .addOnSuccessListener { toast("Photo save ho gayi") }
                    .addOnFailureListener { toast("Error: ${it.localizedMessage}") }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        listener?.remove()
        super.onDestroy()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}