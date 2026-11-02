package com.teach.darbhanga.teacher

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class LauncherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("crash", MODE_PRIVATE)
        val trace = prefs.getString("trace", null)

        if (trace == null) {
            showSplash()
        } else {
            prefs.edit().clear().commit()
            showCrash(trace)
        }
    }

    private fun showSplash() {
        val root = FrameLayout(this)

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL

        val name = TextView(this)
        name.text = "Tuition Manager"
        name.setTextColor(Color.WHITE)
        name.textSize = 26f
        name.setTypeface(null, Typeface.BOLD)
        name.gravity = Gravity.CENTER

        val tag = TextView(this)
        tag.text = "Attendance  •  Fees  •  Tests  •  Leaderboard"
        tag.setTextColor(Color.parseColor("#B3FFFFFF"))
        tag.textSize = 12f
        tag.gravity = Gravity.CENTER
        tag.setPadding(0, 8, 0, 0)

        col.addView(name)
        col.addView(tag)

        val density = resources.displayMetrics.density
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        lp.setMargins(0, (240 * density).toInt(), 0, 0)
        root.addView(col, lp)
        setContentView(root)

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }, 900)
    }

    private fun showCrash(trace: String) {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.WHITE)
        root.setPadding(24, 48, 24, 24)

        val tv = TextView(this)
        tv.text = trace
        tv.textSize = 12f
        tv.setTextColor(Color.BLACK)
        tv.setTextIsSelectable(true)
        val scroll = ScrollView(this)
        scroll.addView(tv)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val copy = Button(this)
        copy.text = "Error Copy Karein"
        copy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("crash", trace))
            Toast.makeText(this, "Copy ho gaya", Toast.LENGTH_SHORT).show()
        }
        val retry = Button(this)
        retry.text = "Dobara Try Karein"
        retry.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        root.addView(copy)
        root.addView(retry)
        setContentView(root)
    }
}