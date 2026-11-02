package com.teach.darbhanga.teacher

import android.content.Context
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object ProfileHelper {

    // Google Drive ka share link ho to direct image link me badal deta hai
    private fun fix(url: String): String {
        val m = Regex("drive\\.google\\.com/file/d/([^/?]+)").find(url)
        return if (m != null) "https://drive.google.com/uc?export=view&id=${m.groupValues[1]}" else url
    }

    fun load(iv: ImageView, url: String?) {
        val u = url?.trim().orEmpty()
        Glide.with(iv.context)
            .load(if (u.isEmpty()) null else fix(u))
            .placeholder(R.drawable.ic_avatar_placeholder)
            .error(R.drawable.ic_avatar_placeholder)
            .fallback(R.drawable.ic_avatar_placeholder)
            .centerCrop()
            .into(iv)
    }

    fun promptAndSave(ctx: Context, current: String?, onSaved: (String) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val et = EditText(ctx)
        et.hint = "Photo ka URL (https://...)"
        et.setText(current ?: "")
        et.setSingleLine(true)

        val pad = UiHelper.dp(ctx, 20)
        val box = FrameLayout(ctx)
        box.setPadding(pad, UiHelper.dp(ctx, 8), pad, 0)
        box.addView(et)

        AlertDialog.Builder(ctx)
            .setTitle("Profile Photo")
            .setMessage("Image ka direct link paste karein (jpg/png ya Google Drive link)")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val u = et.text.toString().trim()
                if (u.isNotEmpty() && !u.startsWith("http")) {
                    Toast.makeText(ctx, "Link http ya https se shuru hona chahiye", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                FirebaseFirestore.getInstance().collection("users").document(uid)
                    .update("photoUrl", u)
                    .addOnSuccessListener { onSaved(u) }
                    .addOnFailureListener {
                        Toast.makeText(ctx, "Save fail: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}