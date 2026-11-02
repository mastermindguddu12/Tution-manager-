package com.teach.darbhanga.teacher

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class LbEntry(
    val id: String,
    val name: String,
    val batch: String,
    val photo: String,
    val score: Double,
    val tests: Double,   // -1 = data nahi
    val quiz: Double,    // -1 = data nahi
    val att: Double      // -1 = data nahi
)

data class LbRanked(val rank: Int, val e: LbEntry)

data class LbSettings(
    val winners: Int = 2,
    val rewards: List<String> = emptyList(),
    val tuitionReward: String = ""
) {
    fun rewardFor(rank: Int): String = rewards.getOrNull(rank - 1)?.trim().orEmpty()
}

data class LbData(
    val month: String,
    val updatedMs: Long,
    val entries: List<LbEntry>
)

object LeaderboardEngine {

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    fun currentMonth(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

    // Same score = same rank
    fun rank(entries: List<LbEntry>, batch: String? = null): List<LbRanked> {
        val src = if (batch == null) entries else entries.filter { it.batch == batch }
        val sorted = src.sortedWith(
            compareByDescending<LbEntry> { it.score }.thenBy { it.name.lowercase() }
        )
        return sorted.map { e -> LbRanked(1 + sorted.count { it.score > e.score }, e) }
    }

    private fun r1(v: Double): Double = Math.round(v * 10) / 10.0

    suspend fun load(): LbData? {
        val d = db.collection("leaderboard").document("current").get().await()
        if (!d.exists()) return null

        @Suppress("UNCHECKED_CAST")
        val raw = d.get("entries") as? List<Map<String, Any?>> ?: emptyList()
        val entries = raw.map { m ->
            LbEntry(
                id = m["id"] as? String ?: "",
                name = m["name"] as? String ?: "",
                batch = m["batch"] as? String ?: "",
                photo = m["photo"] as? String ?: "",
                score = (m["score"] as? Number)?.toDouble() ?: 0.0,
                tests = (m["tests"] as? Number)?.toDouble() ?: -1.0,
                quiz = (m["quiz"] as? Number)?.toDouble() ?: -1.0,
                att = (m["att"] as? Number)?.toDouble() ?: -1.0
            )
        }
        return LbData(
            month = d.getString("month") ?: "",
            updatedMs = d.getTimestamp("updatedAt")?.toDate()?.time ?: 0L,
            entries = entries
        )
    }

    suspend fun loadSettings(): LbSettings {
        val d = db.collection("settings").document("leaderboard").get().await()
        if (!d.exists()) return LbSettings()
        val rewards = (d.get("rewards") as? List<*>)?.map { it?.toString() ?: "" } ?: emptyList()
        return LbSettings(
            winners = (d.getLong("winners") ?: 2L).toInt().coerceIn(1, 5),
            rewards = rewards,
            tuitionReward = d.getString("tuitionReward") ?: ""
        )
    }

    suspend fun saveSettings(s: LbSettings) {
        db.collection("settings").document("leaderboard").set(
            mapOf(
                "winners" to s.winners.toLong(),
                "rewards" to s.rewards,
                "tuitionReward" to s.tuitionReward
            )
        ).await()
    }

    // Sirf teacher ke app se chalta hai (teacher sab data padh sakta hai)
    suspend fun recompute(): LbData {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val keyFmt = SimpleDateFormat("yyyy-MM", Locale.US)
        val month = keyFmt.format(cal.time)
        val start = Timestamp(cal.time)
        cal.add(Calendar.MONTH, 1)
        val end = Timestamp(cal.time)
        val nextKey = keyFmt.format(cal.time)

        val students = db.collection("students").get().await().documents
            .filter { it.getBoolean("active") != false }

        val results = db.collection("results")
            .whereGreaterThanOrEqualTo("savedAt", start)
            .whereLessThan("savedAt", end)
            .get().await().documents
            .filter { it.getBoolean("published") == true }

        val attempts = db.collection("quizAttempts")
            .whereGreaterThanOrEqualTo("createdAt", start)
            .whereLessThan("createdAt", end)
            .get().await().documents

        val att = db.collection("attendance")
            .whereGreaterThanOrEqualTo("date", "$month-01")
            .whereLessThan("date", "$nextKey-01")
            .get().await().documents

        val testPct = HashMap<String, MutableList<Double>>()
        for (d in results) {
            val id = d.getString("studentId") ?: continue
            val marks = d.getDouble("marks") ?: 0.0
            val max = (d.getLong("maxMarks") ?: 0L).toDouble()
            if (max > 0) testPct.getOrPut(id) { mutableListOf() }.add(marks * 100 / max)
        }

        val quizPct = HashMap<String, MutableList<Double>>()
        for (d in attempts) {
            val id = d.getString("studentId") ?: continue
            val sc = (d.getLong("score") ?: 0L).toDouble()
            val tot = (d.getLong("total") ?: 0L).toDouble()
            if (tot > 0) quizPct.getOrPut(id) { mutableListOf() }.add(sc * 100 / tot)
        }

        val attPresent = HashMap<String, Int>()
        val attTotal = HashMap<String, Int>()
        for (d in att) {
            val id = d.getString("studentId") ?: continue
            attTotal[id] = (attTotal[id] ?: 0) + 1
            if (d.getString("status") == "present") attPresent[id] = (attPresent[id] ?: 0) + 1
        }

        val entries = students.map { s ->
            val id = s.id
            val t = testPct[id]?.average() ?: -1.0
            val q = quizPct[id]?.average() ?: -1.0
            val total = attTotal[id] ?: 0
            val a = if (total > 0) (attPresent[id] ?: 0) * 100.0 / total else -1.0

            var sum = 0.0
            var w = 0.0
            if (t >= 0) { sum += t * 50; w += 50 }
            if (q >= 0) { sum += q * 30; w += 30 }
            if (a >= 0) { sum += a * 20; w += 20 }
            val score = if (w > 0) r1(sum / w) else 0.0

            LbEntry(
                id = id,
                name = s.getString("name") ?: "",
                batch = s.getString("batch") ?: "",
                photo = s.getString("photoUrl") ?: "",
                score = score,
                tests = r1(t),
                quiz = r1(q),
                att = r1(a)
            )
        }

        val list = entries.map {
            mapOf(
                "id" to it.id,
                "name" to it.name,
                "batch" to it.batch,
                "photo" to it.photo,
                "score" to it.score,
                "tests" to it.tests,
                "quiz" to it.quiz,
                "att" to it.att
            )
        }
        db.collection("leaderboard").document("current").set(
            mapOf(
                "month" to month,
                "updatedAt" to Timestamp.now(),
                "entries" to list
            )
        ).await()

        return LbData(month, System.currentTimeMillis(), entries)
    }
}