package com.teach.darbhanga.teacher

data class FeeRow(
    val id: String = "",
    val studentId: String = "",
    val name: String = "",
    val batch: String = "",
    val parentPhone: String = "",
    val month: String = "",
    val amount: Long = 0,
    val paid: Boolean = false,
    val photoUrl: String = ""
)