package com.inputblocker.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.Serializable

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "InputBlockerPrefs"
        private const val PREF_THEME = "theme"
        private const val THEME_SYSTEM = 0
        private const val THEME_LIGHT = 1
        private const val THEME_DARK = 2
        private const val THEME_AMOLED = 3
    }

    private lateinit var switchEnabled: Switch
    private lateinit var regionsList: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var btnLaunchSetup: Button
    private lateinit var btnAddRegion: Button
    private lateinit var btnClearAll: Button
    private lateinit var btnTheme: Button

    private var isEnabled = true
    private val regions = mutableListOf<Region>()
    private var currentTheme = THEME_SYSTEM

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                launchSetupActivity()
            } else {
                showOverlayPermissionDenied()
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        checkOverlayPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        loadThemePreference()
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        loadConfig()
        updateUI()
        applyThemeToViews()
    }

    override fun onResume() {
        super.onResume()
        loadConfig()
        updateUI()
        applyThemeToViews()
    }

    private fun loadThemePreference() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        currentTheme = prefs.getInt(PREF_THEME, THEME_SYSTEM)
    }

    private fun saveThemePreference(theme: Int) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putInt(PREF_THEME, theme).apply()
        currentTheme = theme
    }

    private fun applyTheme() {
        when (currentTheme) {
            THEME_LIGHT -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            THEME_DARK -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            THEME_AMOLED -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    private fun getThemeResId(): Int {
        return when (currentTheme) {
            THEME_LIGHT -> R.style.Theme_InputBlocker_Light
            THEME_DARK -> R.style.Theme_InputBlocker_Dark
            THEME_AMOLED -> R.style.Theme_InputBlocker_AMOLED
            else -> R.style.Theme_InputBlocker
        }
    }

    private fun applyThemeToViews() {
        val bgColor = getBackgroundColor()
        val surfaceColor = getSurfaceColor()
        val elevatedColor = getSurfaceElevatedColor()
        val textPrimary = getTextPrimaryColor()

        findViewById<android.view.View>(android.R.id.content)?.setBackgroundColor(bgColor)

        tvStatus?.apply {
            setTextColor(
                if (isEnabled) ContextCompat.getColor(this@MainActivity, R.color.accent_green)
                else ContextCompat.getColor(this@MainActivity, R.color.accent_red)
            )
        }

        btnTheme?.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.accent_blue)
        )

        btnAddRegion?.apply {
            backgroundTintList = ColorStateList.valueOf(elevatedColor)
            setTextColor(textPrimary)
        }
    }

    private fun getBackgroundColor(): Int {
        return when (currentTheme) {
            THEME_LIGHT -> ContextCompat.getColor(this, R.color.light_background)
            THEME_DARK -> ContextCompat.getColor(this, R.color.dark_background)
            THEME_AMOLED -> ContextCompat.getColor(this, R.color.amoled_background)
            else -> ContextCompat.getColor(this, R.color.dark_background)
        }
    }

    private fun getSurfaceColor(): Int {
        return when (currentTheme) {
            THEME_LIGHT -> ContextCompat.getColor(this, R.color.light_surface)
            THEME_DARK -> ContextCompat.getColor(this, R.color.dark_surface)
            THEME_AMOLED -> ContextCompat.getColor(this, R.color.amoled_surface)
            else -> ContextCompat.getColor(this, R.color.dark_surface)
        }
    }

    private fun getSurfaceElevatedColor(): Int {
        return when (currentTheme) {
            THEME_LIGHT -> ContextCompat.getColor(this, R.color.light_surface_elevated)
            THEME_DARK -> ContextCompat.getColor(this, R.color.dark_surface_elevated)
            THEME_AMOLED -> ContextCompat.getColor(this, R.color.amoled_surface_elevated)
            else -> ContextCompat.getColor(this, R.color.dark_surface_elevated)
        }
    }

    private fun getTextPrimaryColor(): Int {
        return when (currentTheme) {
            THEME_LIGHT -> ContextCompat.getColor(this, R.color.light_text_primary)
            THEME_DARK -> ContextCompat.getColor(this, R.color.dark_text_primary)
            THEME_AMOLED -> ContextCompat.getColor(this, R.color.amoled_text_primary)
            else -> ContextCompat.getColor(this, R.color.dark_text_primary)
        }
    }

    private fun getTextSecondaryColor(): Int {
        return when (currentTheme) {
            THEME_LIGHT -> ContextCompat.getColor(this, R.color.light_text_secondary)
            THEME_DARK -> ContextCompat.getColor(this, R.color.dark_text_secondary)
            THEME_AMOLED -> ContextCompat.getColor(this, R.color.amoled_text_secondary)
            else -> ContextCompat.getColor(this, R.color.dark_text_secondary)
        }
    }

    private fun initViews() {
        switchEnabled = findViewById(R.id.switch_enabled)
        regionsList = findViewById(R.id.regions_list)
        tvStatus = findViewById(R.id.tv_status)
        btnLaunchSetup = findViewById(R.id.btn_launch_setup)
        btnAddRegion = findViewById(R.id.btn_add_region)
        btnClearAll = findViewById(R.id.btn_clear_all)
        btnTheme = findViewById(R.id.btn_theme)
    }

    private fun setupListeners() {
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            isEnabled = isChecked
            saveEnabledState(isChecked)
            updateStatus()
        }

        btnLaunchSetup.setOnClickListener { checkOverlayPermission() }
        btnAddRegion.setOnClickListener { showAddRegionDialog() }
        btnClearAll.setOnClickListener { confirmClearAll() }
        btnTheme.setOnClickListener { showThemeDialog() }
    }

    private fun showThemeDialog() {
        val themes = arrayOf("System Default", "Light", "Dark", "AMOLED")

        AlertDialog.Builder(this)
            .setTitle(R.string.select_theme)
            .setSingleChoiceItems(themes, currentTheme) { dialog, which ->
                saveThemePreference(which)
                dialog.dismiss()
                applyTheme()
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                launchSetupActivity()
            } else {
                showOverlayPermissionRequest()
            }
        } else {
            launchSetupActivity()
        }
    }

    private fun showOverlayPermissionRequest() {
        AlertDialog.Builder(this)
            .setTitle("Overlay Permission Required")
            .setMessage("InputBlocker needs permission to display an overlay for visual region setup.")
            .setPositiveButton("Grant") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showOverlayPermissionDenied() {
        Toast.makeText(this, "Overlay permission denied. Visual setup unavailable.", Toast.LENGTH_LONG).show()
    }

    private fun launchSetupActivity() {
        val intent = Intent(this, SetupActivity::class.java)
        intent.putExtra("regions", ArrayList(regions))
        intent.putExtra("theme", currentTheme)
        startActivity(intent)
    }

    private fun loadConfig() {
        regions.clear()

        val configFile = File(InputBlockerServiceManager.getConfigFile(this))
        if (!configFile.exists()) {
            configFile.parentFile?.mkdirs()
            return
        }

        try {
            BufferedReader(FileReader(configFile)).use { reader ->
                reader.lineSequence().forEach { line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.isEmpty() || trimmed.startsWith("#") -> return@forEach
                        trimmed.startsWith("enabled=") -> {
                            isEnabled = trimmed.substring(8) == "1"
                        }
                        else -> {
                            val parts = trimmed.split(",")
                            if (parts.size == 4) {
                                try {
                                    regions.add(Region(
                                        parts[0].trim().toInt(),
                                        parts[1].trim().toInt(),
                                        parts[2].trim().toInt(),
                                        parts[3].trim().toInt()
                                    ))
                                } catch (_: NumberFormatException) { }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveConfig() {
        val configFile = File(InputBlockerServiceManager.getConfigFile(this))
        configFile.parentFile?.mkdirs()

        try {
            FileWriter(configFile).use { writer ->
                writer.write("# InputBlocker Configuration\n")
                writer.write("# Format: x1,y1,x2,y2\n")
                writer.write("# Lines starting with # are comments\n")
                writer.write("#\n")
                writer.write("enabled=${if (isEnabled) "1" else "0"}\n")
                writer.write("\n")
                writer.write("# Blocked regions:\n")

                for (region in regions) {
                    writer.write("${region.x1},${region.y1},${region.x2},${region.y2}\n")
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save config", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun saveEnabledState(enabled: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putBoolean("enabled", enabled).apply()
        loadConfig()
        saveConfig()
    }

    private fun updateUI() {
        switchEnabled.isChecked = isEnabled
        updateStatus()
        updateRegionsList()
    }

    private fun updateStatus() {
        val status = if (isEnabled) "ENABLED" else "DISABLED"
        tvStatus.text = "Status: $status"
        tvStatus.setTextColor(
            if (isEnabled) ContextCompat.getColor(this, R.color.accent_green)
            else ContextCompat.getColor(this, R.color.accent_red)
        )
    }

    private fun updateRegionsList() {
        regionsList.removeAllViews()

        val textColor = getTextSecondaryColor()

        if (regions.isEmpty()) {
            TextView(this).apply {
                text = "No blocked regions configured.\nTap 'Visual Setup' to add regions."
                setTextColor(textColor)
                setPadding(32, 32, 32, 32)
                regionsList.addView(this)
            }
            return
        }

        for (i in regions.indices) {
            addRegionView(i, regions[i])
        }
    }

    private fun addRegionView(index: Int, region: Region) {
        val itemLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(getSurfaceColor())
        }

        val tvRegion = TextView(this).apply {
            val width = region.x2 - region.x1
            val height = region.y2 - region.y1
            text = "[${index + 1}] (${region.x1},${region.y1}) - (${region.x2},${region.y2})\nSize: ${width}x${height}"
            setTextColor(getTextPrimaryColor())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnRemove = Button(this).apply {
            text = "X"
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this@MainActivity, R.color.accent_red)
            )
            setOnClickListener { removeRegion(index) }
        }

        itemLayout.addView(tvRegion)
        itemLayout.addView(btnRemove)
        regionsList.addView(itemLayout)
    }

    private fun showAddRegionDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Add Region Manually")
        builder.setMessage("Enter coordinates in format: x1,y1,x2,y2\n\nExample: 0,0,100,200")

        val input = EditText(this).apply {
            hint = "0,0,100,200"
            setPadding(48, 32, 48, 32)
        }
        builder.setView(input)

        builder.setPositiveButton("Add") { _, _ ->
            val coordsStr = input.text.toString().trim()
            val region = parseRegion(coordsStr)
            if (region != null) {
                regions.add(region)
                saveConfig()
                updateUI()
                Toast.makeText(this, "Region added", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Invalid coordinates", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun parseRegion(coords: String): Region? {
        return try {
            val parts = coords.split(",")
            if (parts.size != 4) return null

            val region = Region(
                parts[0].trim().toInt(),
                parts[1].trim().toInt(),
                parts[2].trim().toInt(),
                parts[3].trim().toInt()
            )

            if (region.x1 >= region.x2 || region.y1 >= region.y2) return null

            region
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun removeRegion(index: Int) {
        if (index in regions.indices) {
            regions.removeAt(index)
            saveConfig()
            updateUI()
            Toast.makeText(this, "Region removed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle("Clear All Regions")
            .setMessage("Are you sure you want to remove all blocked regions?")
            .setPositiveButton("Clear All") { _, _ ->
                regions.clear()
                saveConfig()
                updateUI()
                Toast.makeText(this, "All regions cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun setRegions(newRegions: List<Region>) {
        regions.clear()
        regions.addAll(newRegions)
        saveConfig()
        runOnUiThread { updateUI() }
    }

    data class Region(
        var x1: Int = 0,
        var y1: Int = 0,
        var x2: Int = 0,
        var y2: Int = 0
    ) : Serializable
}
