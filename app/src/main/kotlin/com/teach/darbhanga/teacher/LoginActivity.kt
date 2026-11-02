package com.teach.darbhanga.teacher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.teach.darbhanga.teacher.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginActivity : AppCompatActivity() {

    private lateinit var b: ActivityLoginBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Pehle se login hai to seedha dashboard
        auth.currentUser?.let { routeByRole(it.uid) }

        b.btnLogin.setOnClickListener { attemptLogin() }

        b.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else false
        }
    }

    private fun attemptLogin() {
        val email = b.etEmail.text.toString().trim()
        val pass = b.etPassword.text.toString()
        if (email.isEmpty() || pass.isEmpty()) {
            toast("Email aur password daalein")
            return
        }
        hideKeyboard()
        login(email, pass)
    }

    private fun login(email: String, pass: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val result = auth.signInWithEmailAndPassword(email, pass).await()
                routeByRole(result.user!!.uid)
            } catch (e: Exception) {
                setLoading(false)
                toast(friendly(e))
            }
        }
    }

    private fun routeByRole(uid: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val doc = db.collection("users").document(uid).get().await()
                val role = doc.getString("role")
                val name = doc.getString("name") ?: ""
                val target = when (role) {
                    "teacher" -> TeacherDashboardActivity::class.java
                    "student" -> StudentDashboardActivity::class.java
                    else -> null
                }
                if (target == null) {
                    auth.signOut()
                    setLoading(false)
                    toast("Is account ka role set nahi hai. Teacher se sampark karein.")
                    return@launch
                }
                startActivity(Intent(this@LoginActivity, target).putExtra("name", name))
                finish()
            } catch (e: Exception) {
                auth.signOut()
                setLoading(false)
                toast("Profile load nahi hui: ${friendly(e)}")
            }
        }
    }

    private fun friendly(e: Exception): String = when (e) {
        is FirebaseAuthInvalidUserException -> "Ye email registered nahi hai"
        is FirebaseAuthInvalidCredentialsException -> "Email ya password galat hai"
        is FirebaseNetworkException -> "Internet check karein aur dobara try karein"
        else -> e.localizedMessage ?: "Kuch gadbad hui, dobara try karein"
    }

    private fun setLoading(show: Boolean) {
        b.progress.visibility = if (show) View.VISIBLE else View.GONE
        b.btnLogin.isEnabled = !show
        b.btnLogin.text = if (show) "Please wait..." else "Login"
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(b.root.windowToken, 0)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}