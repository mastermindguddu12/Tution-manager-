package com.teach.darbhanga.teacher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

object FcmHelper {

    // Activity ke onCreate me hi call karein
    fun setup(activity: AppCompatActivity) {
        val launcher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                activity, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        saveToken()
    }

    fun saveToken() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .update("fcmToken", token)
        }
    }
}