package com.inputblocker.app

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.io.File

class CrashLogActivity : AppCompatActivity() {

    companion object {
        private val CRASH_LOG_DIR = InputBlockerServiceManager.getCrashCountDir() + "/crash_logs"
        private const val MAX_LOG_LINES = 1000
    }

    private lateinit var logContainer: LinearLayout
    private lateinit var btnClear: MaterialButton
    private lateinit var btnRefresh: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentTheme = getSharedPreferences("InputBlockerPrefs", MODE_PRIVATE)
            .getInt("theme", ThemeManager.THEME_SYSTEM)
        val colors = ThemeManager.getThemeColors(this@CrashLogActivity, currentTheme)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(colors.background)
        }

        val title = TextView(this).apply {
            text = "Crash Log Viewer"
            textSize = 24f
            setTextColor(colors.textPrimary)
            setPadding(0, 0, 0, 32)
        }

        // Button row
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        btnRefresh = MaterialButton(this).apply {
            text = "Refresh"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { refreshLog() }
        }

        btnClear = MaterialButton(this).apply {
            text = "Clear All"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(android.graphics.Color.RED)
            setOnClickListener {
                try {
                    val crashDir = File(CRASH_LOG_DIR)
                    if (crashDir.exists()) {
                        crashDir.listFiles()?.forEach { it.delete() }
                    }
                    refreshLog()
                    Toast.makeText(this@CrashLogActivity, "Crash logs cleared", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@CrashLogActivity, "Clear failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        buttonLayout.addView(btnRefresh)
        buttonLayout.addView(btnClear)

        // Scrollable log container
        logContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scrollView = ScrollView(this).apply {
            addView(logContainer)
        }

        root.addView(title)
        root.addView(buttonLayout)
        root.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        setContentView(root)
        refreshLog()
    }

    private fun refreshLog() {
        logContainer.removeAllViews()

        try {
            val crashDir = File(CRASH_LOG_DIR)
            if (!crashDir.exists() || crashDir.listFiles().isNullOrEmpty()) {
                showEmptyLog("No crash logs found.")
                return
            }

            val crashFiles = crashDir.listFiles()
                ?.filter { it.isFile }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()

            val currentTheme = getSharedPreferences("InputBlockerPrefs", MODE_PRIVATE)
                .getInt("theme", ThemeManager.THEME_SYSTEM)
            val colors = ThemeManager.getThemeColors(this@CrashLogActivity, currentTheme)

            for (file in crashFiles) {
                val card = MaterialCardView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.setMargins(0, 0, 0, 12) }
                    radius = 16f
                    setContentPadding(16, 12, 16, 12)
                    setCardBackgroundColor(colors.surface)
                    strokeColor = colors.border
                    strokeWidth = 1
                }

                val innerLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                }

                val headerText = "${file.name}  —  ${java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()
                ).format(java.util.Date(file.lastModified()))}"

                val header = TextView(this).apply {
                    text = headerText
                    textSize = 13f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(android.graphics.Color.parseColor("#FF5252"))
                    setPadding(0, 0, 0, 8)
                }

                val content = try {
                    file.readText().take(MAX_LOG_LINES)
                } catch (e: Exception) {
                    "Error reading crash file: ${e.message}"
                }

                val body = TextView(this).apply {
                    text = content
                    textSize = 11f
                    setPadding(12, 8, 12, 8)
                    setBackgroundColor(android.graphics.Color.parseColor("#1A000000"))
                    setTextColor(colors.textSecondary)
                    setLineSpacing(4f, 1f)
                    setTypeface(android.graphics.Typeface.MONOSPACE)
                }

                innerLayout.addView(header)
                innerLayout.addView(body)
                card.addView(innerLayout)
                logContainer.addView(card)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error reading crash logs: ${e.message}", Toast.LENGTH_SHORT).show()
            showEmptyLog("Error: ${e.message}")
        }
    }

    private fun showEmptyLog(msg: String) {
        val emptyView = TextView(this).apply {
            text = msg
            textSize = 16f
            setTextColor(android.graphics.Color.GRAY)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 64, 0, 0)
        }
        logContainer.addView(emptyView)
    }

    override fun onResume() {
        super.onResume()
        refreshLog()
    }
}
