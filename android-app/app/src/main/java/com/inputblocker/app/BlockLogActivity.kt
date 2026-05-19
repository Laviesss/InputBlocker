package com.inputblocker.app

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.io.Serializable
import java.util.*

class BlockLogActivity : AppCompatActivity() {

    private lateinit var logContainer: LinearLayout
    private lateinit var btnClearLog: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            val currentTheme = getSharedPreferences("InputBlockerPrefs", MODE_PRIVATE).getInt("theme", ThemeManager.THEME_SYSTEM)
            val colors = ThemeManager.getThemeColors(this@BlockLogActivity, currentTheme)
            setBackgroundColor(colors.background)
        }

        val title = TextView(this).apply {
            text = "Real-time Block Log"
            textSize = 24f
            val currentTheme = getSharedPreferences("InputBlockerPrefs", MODE_PRIVATE).getInt("theme", ThemeManager.THEME_SYSTEM)
            val colors = ThemeManager.getThemeColors(this@BlockLogActivity, currentTheme)
            setTextColor(colors.textPrimary)
            setPadding(0, 0, 0, 32)
        }

        btnClearLog = Button(this).apply {
            text = "Clear Log"
            setOnClickListener {
                try {
                    val logFile = File(InputBlockerServiceManager.getModulePath(this@BlockLogActivity) + "/config/blocklog.txt")
                    if (logFile.exists()) {
                        logFile.delete()
                    }
                    refreshLog()
                    Toast.makeText(this@BlockLogActivity, "Log cleared", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@BlockLogActivity, "Failed to clear log: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        logContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 0)
        }

        root.addView(title)
        root.addView(btnClearLog)
        root.addView(logContainer)

        setContentView(root)
        refreshLog()
    }

    private fun refreshLog() {
        logContainer.removeAllViews()
        
        try {
            val logFile = File(InputBlockerServiceManager.getModulePath(this) + "/config/blocklog.txt")
            if (!logFile.exists()) {
                showEmptyLog()
                return
            }

            val logs = logFile.readLines().reversed().take(100)
            
            if (logs.isEmpty()) {
                showEmptyLog()
                return
            }

            for (line in logs) {
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 0, 0, 16)
                }
                
                val detail = TextView(this).apply {
                    text = line
                    textSize = 14f
                    val currentTheme = getSharedPreferences("InputBlockerPrefs", MODE_PRIVATE).getInt("theme", ThemeManager.THEME_SYSTEM)
                    val colors = ThemeManager.getThemeColors(this@BlockLogActivity, currentTheme)
                    setTextColor(colors.textPrimary)
                }
                
                item.addView(detail)
                logContainer.addView(item)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error reading log: ${e.message}", Toast.LENGTH_SHORT).show()
            showEmptyLog()
        }
    }

    private fun showEmptyLog() {
        val emptyView = TextView(this).apply {
            text = "No blocked touches recorded yet."
            setTextColor(ContextCompat.getColor(this@BlockLogActivity, android.R.color.darker_gray))
            gravity = android.view.Gravity.CENTER
        }
        logContainer.addView(emptyView)
    }

    override fun onResume() {
        super.onResume()
        refreshLog()
    }

    data class BlockEntry(val timestamp: String, val message: String) : Serializable
}
