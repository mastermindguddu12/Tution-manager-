package com.teach.darbhanga.teacher

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

object SecondaryAuth {
    fun get(context: Context): FirebaseAuth {
        val name = "secondary"
        val app = try {
            FirebaseApp.getInstance(name)
        } catch (e: IllegalStateException) {
            FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, name)
        }
        return FirebaseAuth.getInstance(app)
    }
}