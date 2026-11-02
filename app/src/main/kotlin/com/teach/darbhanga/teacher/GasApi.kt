package com.teach.darbhanga.teacher

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GasApi {

    private const val GAS_URL = "https://script.google.com/macros/s/AKfycbzwUzvmKjtXPik0iOTDCV5bQWFSkupAc0PcyurA7lQsCS0oDmgBohWV4M9AG8EHRC6lYw/exec"

    suspend fun call(payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        try {
            val user = FirebaseAuth.getInstance().currentUser
                ?: return@withContext JSONObject().put("ok", false).put("error", "Login nahi hai")
            val token = user.getIdToken(false).await().token
            payload.put("idToken", token)

            val conn = URL(GAS_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 20000
            conn.readTimeout = 60000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(payload.toString().toByteArray()) }

            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val text = stream.bufferedReader().use { it.readText() }
            try {
                JSONObject(text)
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("error", "Server response: " + text.take(150))
            }
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", e.localizedMessage ?: "Network error")
        }
    }
}