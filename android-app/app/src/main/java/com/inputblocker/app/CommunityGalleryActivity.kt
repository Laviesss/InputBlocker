package com.inputblocker.app

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * Senior Engineering: Community Gallery UI.
 * Provides a clean interface for browsing and importing shared configs.
 */
class CommunityGalleryActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            val currentTheme = getSharedPreferences("InputBlockerPrefs", MODE_PRIVATE).getInt("theme", ThemeManager.THEME_SYSTEM)
            val colors = ThemeManager.getThemeColors(this@CommunityGalleryActivity, currentTheme)
            setBackgroundColor(colors.background)
        }

        val title = TextView(this).apply {
            text = "Community Presets"
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            val currentTheme = getSharedPreferences("InputBlockerPrefs", MODE_PRIVATE).getInt("theme", ThemeManager.THEME_SYSTEM)
            val colors = ThemeManager.getThemeColors(this@CommunityGalleryActivity, currentTheme)
            setTextColor(colors.textPrimary)
            setPadding(0, 0, 0, 16)
        }

        val subtitle = TextView(this).apply {
            text = "Verified configurations for failing displays."
            textSize = 14f
            val currentTheme = getSharedPreferences("InputBlockerPrefs", MODE_PRIVATE).getInt("theme", ThemeManager.THEME_SYSTEM)
            val colors = ThemeManager.getThemeColors(this@CommunityGalleryActivity, currentTheme)
            setTextColor(colors.textSecondary)
            setPadding(0, 0, 0, 32)
        }

        progressBar = ProgressBar(this)
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(progressBar)
        root.addView(container)

        setContentView(root)
        loadPresets()
    }

    private fun loadPresets() {
        GalleryManager.fetchPresets(object : GalleryManager.GalleryCallback {
            override fun onPresetsLoaded(presets: List<GalleryManager.PresetMetadata>) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    presets.forEach { addPresetCard(it) }
                }
            }

            override fun onError(message: String) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@CommunityGalleryActivity, "Gallery Error: $message", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun addPresetCard(preset: GalleryManager.PresetMetadata) {
        val colors = ThemeManager.getThemeColors(this, getSharedPreferences("InputBlockerPrefs", MODE_PRIVATE).getInt("theme", ThemeManager.THEME_SYSTEM))
        
        val card = MaterialCardView(this).apply {
            radius = 24f
            strokeWidth = 2
            strokeColor = colors.border
            setCardBackgroundColor(colors.surface)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val modelText = TextView(this).apply {
            text = "📱 ${preset.deviceModel}"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(colors.textPrimary)
        }

        val descText = TextView(this).apply {
            text = preset.description
            textSize = 14f
            setTextColor(colors.textSecondary)
            setPadding(0, 8, 0, 16)
        }

        val importBtn = MaterialButton(this)
        importBtn.text = "IMPORT (${preset.regionCount} regions)"
        importBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(colors.accent)
        importBtn.setTextColor(android.graphics.Color.WHITE)
        importBtn.setOnClickListener {
            importBtn.isEnabled = false
            importBtn.text = "IMPORTING..."
            doImport(preset)
        }

        layout.addView(modelText)
        layout.addView(descText)
        layout.addView(importBtn)
        card.addView(layout)
        container.addView(card)
    }

    private fun doImport(preset: GalleryManager.PresetMetadata) {
        GalleryManager.importPreset(this, preset, object : GalleryManager.ImportCallback {
            override fun onSuccess() {
                runOnUiThread {
                    Toast.makeText(this@CommunityGalleryActivity, "Import Successful!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }

            override fun onError(message: String) {
                runOnUiThread {
                    Toast.makeText(this@CommunityGalleryActivity, "Import Failed: $message", Toast.LENGTH_LONG).show()
                }
            }
        })
    }
}
