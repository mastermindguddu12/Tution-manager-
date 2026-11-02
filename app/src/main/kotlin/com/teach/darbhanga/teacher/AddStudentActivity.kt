package com.teach.darbhanga.teacher

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.teach.darbhanga.teacher.databinding.ActivityAddStudentBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AddStudentActivity : AppCompatActivity() {

    private lateinit var b: ActivityAddStudentBinding
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAddStudentBinding.inflate(layoutInflater)
        setContentView(b.root)

        ProfileHelper.load(b.ivPreview, null)

        b.tvBack.setOnClickListener { finish() }
        b.btnSave.setOnClickListener { save() }

        // Link paste karke bahar nikalte hi preview dikhe
        b.etPhoto.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) ProfileHelper.load(b.ivPreview, b.etPhoto.text.toString())
        }
    }

    private fun save() {
        val name = b.etName.text.toString().trim()
        val email = b.etEmail.text.toString().trim()
        val pass = b.etPassword.text.toString()
        val phone = b.etPhone.text.toString().trim()
        val parentPhone = b.etParentPhone.text.toString().trim()
        val batchName = b.etBatch.text.toString().trim()
        val fee = b.etFee.text.toString().trim().toLongOrNull()
        val photo = b.etPhoto.text.toString().trim()

        when {
            name.isEmpty() -> return toast("Naam daalein")
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> return toast("Sahi email daalein")
            pass.length < 6 -> return toast("Password kam se kam 6 akshar ka ho")
            phone.isNotEmpty() && phone.length != 10 -> return toast("Student ka number 10 digit ka ho ya khali chhodein")
            parentPhone.length != 10 -> return toast("Parent ka 10 digit number daalein")
            batchName.isEmpty() -> return toast("Class/Batch daalein")
            fee == null -> return toast("Fee daalein")
            photo.isNotEmpty() && !photo.startsWith("http") -> return toast("Photo link http ya https se shuru hona chahiye")
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                // Secondary instance: teacher logout nahi hoga
                val sAuth = SecondaryAuth.get(this@AddStudentActivity)
                val uid = sAuth.createUserWithEmailAndPassword(email, pass).await().user!!.uid
                sAuth.signOut()

                val wb = db.batch()
                wb.set(
                    db.collection("users").document(uid),
                    mapOf(
                        "role" to "student",
                        "name" to name,
                        "phone" to phone,
                        "parentPhone" to parentPhone,
                        "photoUrl" to photo
                    )
                )
                wb.set(
                    db.collection("students").document(uid),
                    mapOf(
                        "name" to name,
                        "email" to email,
                        "phone" to phone,
                        "parentPhone" to parentPhone,
                        "batch" to batchName,
                        "monthlyFee" to fee,
                        "photoUrl" to photo,
                        "joinDate" to Timestamp.now(),
                        "active" to true
                    )
                )
                wb.commit().await()

                toast("$name add ho gaya")
                finish()
            } catch (e: Exception) {
                setLoading(false)
                toast("Error: ${e.localizedMessage}")
            }
        }
    }

    private fun setLoading(show: Boolean) {
        b.progress.visibility = if (show) View.VISIBLE else View.GONE
        b.btnSave.isEnabled = !show
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}