package com.inputblocker.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class BlockLogActivity : AppCompatActivity() {

    private lateinit var logContainer: LinearLayout
    private lateinit var btnClearLog: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Simple layout programmatically to avoid extra XML files for a debug tool
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(ContextCompat.getColor(this@BlockLogActivity, R.color.app_background))
        }

        val title = TextView(this).apply {
            text = "Real-time Block Log"
            textSize = 24f
            setTextColor(ContextCompat.getColor(this@BlockLogActivity, R.color.app_text_primary))
            setPadding(0, 0, 0, 32)
        }

        btnClearLog = Button(this).apply {
            text = "Clear Log"
            setOnClickListener {
                logContainer.removeAllViews()
                Toast.makeText(this@BlockLogActivity, "Log cleared", Toast.LENGTH_SHORT).show()
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
        
        // Initial load of the log
        refreshLog()
    }

    private fun refreshLog() {
        logContainer.removeAllViews()
        val logs = OverlayService.getRecentBlocks()
        
        if (logs.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No blocked touches recorded yet."
                setTextColor(ContextCompat.getColor(this@BlockLogActivity, R.color.app_text_secondary))
                gravity = android.view.Gravity.CENTER
            }
            logContainer.addView(emptyView)
            return
        }

        logs.forEach { log ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 16)
            }
            
            val time = TextView(this).apply {
                text = log.timestamp
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@BlockLogActivity, R.color.app_text_secondary))
            }
            
            val detail = TextView(this).apply {
                text = log.message
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@BlockLogActivity, R.color.app_text_primary))
            }
            
            item.addView(time)
            item.addView(detail)
            logContainer.addView(item)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLog()
    }

    data class BlockEntry(val timestamp: String, val message: String)
}
