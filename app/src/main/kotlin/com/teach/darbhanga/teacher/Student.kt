package com.teach.darbhanga.teacher

data class Student(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val parentPhone: String = "",
    val batch: String = "",
    val monthlyFee: Long = 0,
    val photoUrl: String = ""
)