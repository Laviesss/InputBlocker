package com.inputblocker.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.Serializable
import java.util.*

class BlockLogActivity : AppCompatActivity() {

    companion object {
        private const val MAX_LOG_ENTRIES = 1000
    }

    private lateinit var logContainer: LinearLayout
    private lateinit var btnClearLog: Button
    private lateinit var btnShareLog: Button

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

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        btnClearLog = Button(this).apply {
            text = "Clear Log"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
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

        btnShareLog = Button(this).apply {
            text = "Share Log"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                shareLogFile()
            }
        }

        buttonLayout.addView(btnClearLog)
        buttonLayout.addView(btnShareLog)

        logContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 0)
        }

        root.addView(title)
        root.addView(buttonLayout)
        root.addView(logContainer)

        setContentView(root)
        refreshLog()
    }

    private fun pruneLog(logFile: File) {
        try {
            val lines = logFile.readLines()
            if (lines.size > MAX_LOG_ENTRIES) {
                val pruned = lines.drop(lines.size - MAX_LOG_ENTRIES)
                logFile.writeText(pruned.joinToString("\n"))
            }
        } catch (e: Exception) {
            // Silently handle — pruning is best-effort
        }
    }

    private fun shareLogFile() {
        try {
            val rootLogFile = File(InputBlockerServiceManager.getModulePath(this) + "/config/blocklog.txt")
            if (!rootLogFile.exists()) {
                Toast.makeText(this, "No log file found to share", Toast.LENGTH_SHORT).show()
                return
            }

            // Copy root log to app's internal cache so FileProvider can share it
            val cacheFile = File(cacheDir, "blocklog_export.txt")
            rootLogFile.inputStream().use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }

            val contentUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                cacheFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Block Log"))

        } catch (e: Exception) {
            Toast.makeText(this, "Failed to share log: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshLog() {
        logContainer.removeAllViews()
        
        try {
            val logFile = File(InputBlockerServiceManager.getModulePath(this) + "/config/blocklog.txt")
            if (!logFile.exists()) {
                showEmptyLog()
                return
            }

            // Auto-prune: keep only last MAX_LOG_ENTRIES lines
            pruneLog(logFile)

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
