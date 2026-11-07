package ru.lectograph.app.models

data class Lecture(
    val id: Long, val title: String, val subject: String, val notes: String, val audio: String,
    val created: Long, val duration: Long, val status: String
)
