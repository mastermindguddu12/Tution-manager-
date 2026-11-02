package com.teach.darbhanga.teacher

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object Batches {
    suspend fun load(): List<String> {
        val snap = FirebaseFirestore.getInstance().collection("students").get().await()
        return snap.documents
            .mapNotNull { it.getString("batch") }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }
}