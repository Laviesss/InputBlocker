package com.inputblocker.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.switchmaterial.SwitchMaterial
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.ArrayList

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "InputBlockerPrefs"
        private const val PREF_THEME = "theme"
        private const val PREF_DBSCAN_EPS = "dbscan_eps"
        private const val PREF_DBSCAN_MINPTS = "dbscan_minpts"
        private const val THEME_SYSTEM = 0
        private const val THEME_LIGHT = 1
        private const val THEME_DARK = 2
        private const val THEME_AMOLED = 3
    }

    private lateinit var switchEnabled: SwitchMaterial
    private lateinit var regionsList: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var btnLaunchSetup: Button
    private lateinit var btnAddRegion: Button
    private lateinit var btnClearAll: Button
    private lateinit var btnTheme: Button
    private lateinit var btnAutoDetect: Button

    // Quick Actions views
    private lateinit var fabQuickActions: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var layoutQuickActions: LinearLayout
    private lateinit var btnActionSafe: Button
    private lateinit var btnActionSync: Button
    private lateinit var btnActionExport: Button
    private lateinit var btnActionTest: Button

    private var isEnabled = true
    private var isLsposedMode = false
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
        setupQuickActions()
        setupDetectionReceiver()
        loadConfig()
        updateUI()
        applyThemeToViews()
        
        checkForUpdates()
        
        handleQuickActionIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQuickActionIntent(intent)
    }

    private fun handleQuickActionIntent(intent: Intent?) {
        if (intent?.action == "com.inputblocker.ACTION_QUICK_MENU") {
            layoutQuickActions.visibility = View.VISIBLE
        }
    }

    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_check_update -> {
                checkForUpdates(force = true)
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun checkForUpdates(force: Boolean = false) {
        UpdateChecker.checkForUpdate(object : UpdateChecker.UpdateCallback {
            override fun onUpdateAvailable(info: UpdateChecker.UpdateInfo, currentVersion: String) {
                runOnUiThread {
                    showUpdateDialog(info, currentVersion)
                }
            }
            
            override fun onNoUpdateAvailable(currentVersion: String) {
                runOnUiThread {
                    if (force) {
                        Toast.makeText(this@MainActivity, "You're on the latest version ($currentVersion)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
            override fun onError(error: String) {
                runOnUiThread {
                    if (force) {
                        Toast.makeText(this@MainActivity, "Update check failed: $error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
    
    private fun showUpdateDialog(info: UpdateChecker.UpdateInfo, currentVersion: String) {
        AlertDialog.Builder(this)
            .setTitle("Update Available")
            .setMessage("A new version (${info.version}) is available!\n\nYou have: $currentVersion\n\nWould you like to download it?")
            .setPositiveButton("Download") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl))
                startActivity(intent)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun showProfileDialog() {
        val profiles = arrayOf("default", "gaming", "media", "work") // Basic presets
        
        AlertDialog.Builder(this)
            .setTitle("Select Profile")
            .setSingleChoiceItems(profiles, 0) { _, which ->
                val selectedProfile = profiles[which]
                val intent = Intent("com.inputblocker.CHANGE_PROFILE")
                intent.putExtra("profile", selectedProfile)
                sendBroadcast(intent)
                Toast.makeText(this, "Profile switched to: $selectedProfile", Toast.LENGTH_SHORT).show()
                loadConfig()
                updateUI()
            }
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showAboutDialog() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "Unknown"
        }
        
        AlertDialog.Builder(this)
            .setTitle("About InputBlocker")
            .setMessage("InputBlocker v$version\n\nBlock ghost taps and unwanted touch inputs.\n\nCreated by Laviesss")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        loadConfig()
        updateUI()
        applyThemeToViews()
    }

    private fun setupDetectionReceiver() {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                if (intent?.action == "com.inputblocker.DETECTION_RESULTS") {
                    @Suppress("DEPRECATION")
                    val capturedRegions = intent.getSerializableExtra("regions") as? ArrayList<Region>
                    if (!capturedRegions.isNullOrEmpty()) {
                        showDetectionReview(capturedRegions)
                    } else {
                        Toast.makeText(this@MainActivity, "No ghost taps detected.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        
        val filter = IntentFilter("com.inputblocker.DETECTION_RESULTS")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun showDetectionReview(suggestedRegions: List<Region>) {
        val intent = Intent(this, DetectionReviewActivity::class.java)
        intent.putExtra("regions", ArrayList(suggestedRegions))
        startActivity(intent)
    }

    private fun clusterTouches(touches: List<Pair<Float, Float>>): List<Region> {
        if (touches.isEmpty()) return emptyList()
        
        val regionsResult = mutableListOf<Region>()
        val used = BooleanArray(touches.size) { false }
        val threshold = 0.05f // 5% of screen width/height
        
        for (i in touches.indices) {
            if (used[i]) continue
            
            var minX = touches[i].first
            var maxX = touches[i].first
            var minY = touches[i].second
            var maxY = touches[i].second
            
            used[i] = true
            
            // Simple iterative expansion clustering
            var changed = true
            while (changed) {
                changed = false
                for (j in touches.indices) {
                    if (used[j]) continue
                    val tx = touches[j].first
                    val ty = touches[j].second
                    
                    if (tx >= minX - threshold && tx <= maxX + threshold &&
                        ty >= minY - threshold && ty <= maxY + threshold) {
                        
                        minX = minOf(minX, tx)
                        maxX = maxOf(maxX, tx)
                        minY = minOf(minY, ty)
                        maxY = maxOf(maxY, ty)
                        
                        used[j] = true
                        changed = true
                    }
                }
            }
            regionsResult.add(Region(minX, minY, maxX, maxY))
        }
        return regionsResult
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

    private fun applyThemeToViews() {
        val bgColor = getBackgroundColor()
        val elevatedColor = getSurfaceElevatedColor()
        val textPrimary = getTextPrimaryColor()
        
        findViewById<android.view.View>(android.R.id.content)?.setBackgroundColor(bgColor)
        
        tvStatus.apply {
            setTextColor(
                if (isEnabled) ContextCompat.getColor(this@MainActivity, R.color.accent_green)
                else ContextCompat.getColor(this@MainActivity, R.color.accent_red)
            )
        }
        
        btnTheme.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.accent_blue)
        )
        
        btnAddRegion.apply {
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
        btnAutoDetect = findViewById(R.id.btn_auto_detect)
        
        // Quick Actions
        fabQuickActions = findViewById(R.id.fab_quick_actions)
        layoutQuickActions = findViewById(R.id.layout_quick_actions)
        btnActionSafe = findViewById(R.id.btn_action_safe)
        btnActionSync = findViewById(R.id.btn_action_sync)
        btnActionExport = findViewById(R.id.btn_action_export)
        btnActionTest = findViewById(R.id.btn_action_test)
    }

    private fun setupListeners() {
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            isEnabled = isChecked
            saveEnabledState(isChecked)
            updateStatus()
        }
        
        // LSPosed Mode Toggle
        findViewById<SwitchMaterial>(R.id.switch_lsposed).setOnCheckedChangeListener { _, isChecked ->
            isLsposedMode = isChecked
            saveLsposedPreference(isChecked)
            updateStatus()
            
            if (isChecked) {
                Toast.makeText(this, "LSPosed Mode enabled. Please enable this module in LSPosed Manager.", Toast.LENGTH_LONG).show()
            }
        }

        btnLaunchSetup.setOnClickListener { checkOverlayPermission() }
        btnAddRegion.setOnClickListener { showAddRegionDialog() }
        btnClearAll.setOnClickListener { confirmClearAll() }
        btnTheme.setOnClickListener { showThemeDialog() }
        btnAutoDetect.setOnClickListener { startAutoDetection() }
        
        // Profile Selection Trigger (Optional)
        // findViewById<Button>(R.id.btn_profile)?.setOnClickListener { showProfileDialog() }
    }

    private fun setupQuickActions() {
        fabQuickActions.setOnClickListener {
            val isVisible = layoutQuickActions.visibility == View.VISIBLE
            layoutQuickActions.visibility = if (isVisible) View.GONE else View.VISIBLE
        }

        btnActionSafe.setOnClickListener {
            isEnabled = false
            saveEnabledState(false)
            regions.clear()
            saveConfig()
            updateUI()
            updateStatus()
            layoutQuickActions.visibility = View.GONE
            Toast.makeText(this, "Safe Mode: Blocking disabled and regions cleared", Toast.LENGTH_LONG).show()
        }

        btnActionSync.setOnClickListener {
            loadConfig()
            updateUI()
            layoutQuickActions.visibility = View.GONE
            Toast.makeText(this, "Configuration synced with root module", Toast.LENGTH_SHORT).show()
        }

        btnActionExport.setOnClickListener {
            exportConfig()
            layoutQuickActions.visibility = View.GONE
        }

        btnActionTest.setOnClickListener {
            try {
                val testFile = File("/data/adb/modules/inputblocker/config/test_mode")
                testFile.createNewFile()
                
                AlertDialog.Builder(this)
                    .setTitle("Hook Test Active")
                    .setMessage("Test mode is now ACTIVE for 5 seconds.\n\nTry tapping anywhere on your screen. If touches are blocked, the hook is working correctly!")
                    .setPositiveButton("OK", null)
                    .show()

                // Remove test mode after 5 seconds
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (testFile.exists()) {
                        testFile.delete()
                    }
                }, 5000)
            } catch (e: Exception) {
                Toast.makeText(this, "Test failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            layoutQuickActions.visibility = View.GONE
        }
    }

    private fun exportConfig() {
        try {
            val configFile = File(InputBlockerServiceManager.getConfigFile(this))
            if (!configFile.exists()) {
                Toast.makeText(this, "No config file found to export", Toast.LENGTH_SHORT).show()
                return
            }

            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                configFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Export Config"))
        } catch (e: Exception) {
            Log.e("MainActivity", "Export failed", e)
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startAutoDetection() {
        Thread {
            try {
                Log.i("MainActivity", "Starting Auto-Detection sequence...")
                
                // 1. Disable screen lock
                InputBlockerServiceManager.runRootCommand("settings put secure lockscreen.disabled 1")
                Log.i("MainActivity", "Screen lock disabled")
                
                Thread.sleep(500)
                
                // 2. Turn off screen
                InputBlockerServiceManager.runRootCommand("input keyevent 26")
                Log.i("MainActivity", "Screen turned off")
                
                Thread.sleep(2000) // Wait for device to settle
                
                // 3. Turn screen back on
                InputBlockerServiceManager.runRootCommand("input keyevent KEYCODE_WAKEUP")
                Log.i("MainActivity", "Screen turned on")
                
                Thread.sleep(1000)
                
                // 4. Launch Sensing Screen
                runOnUiThread {
                    val intent = Intent(this, SensingActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    Toast.makeText(this, "Sensing mode active!", Toast.LENGTH_LONG).show()
                }
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Auto-detection sequence failed", e)
                runOnUiThread {
                    Toast.makeText(this, "Detection failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
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
        
        // Use the default profile for loading region list in MainActivity
        val configFile = File(InputBlockerServiceManager.getConfigFile(this, "default"))
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
                        trimmed.startsWith("lsposed_mode=") -> {
                            isLsposedMode = trimmed.substring(13) == "1"
                        }
                        else -> {
                            val parts = trimmed.split(",")
                            if (parts.size == 4) {
                                try {
                                    regions.add(Region(
                                        parts[0].trim().toFloat(),
                                        parts[1].trim().toFloat(),
                                        parts[2].trim().toFloat(),
                                        parts[3].trim().toFloat()
                                    ))
                                } catch (e: NumberFormatException) {
                                    // Skip invalid lines
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading config", e)
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

    private fun saveLsposedPreference(enabled: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putBoolean("lsposed_mode", enabled).apply()
        
        // We also add it to the config file so the Xposed module can see it
        val configFile = File(InputBlockerServiceManager.getConfigFile(this))
        try {
            var content = configFile.readText()
            if (content.contains("lsposed_mode=")) {
                content = content.replace(Regex("lsposed_mode=.*"), "lsposed_mode=${if (enabled) "1" else "0"}")
            } else {
                content += "\nlsposed_mode=${if (enabled) "1" else "0"}"
            }
            configFile.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateUI() {
        switchEnabled.isChecked = isEnabled
        findViewById<SwitchMaterial>(R.id.switch_lsposed).isChecked = isLsposedMode
        updateStatus()
        updateRegionsList()
    }

    private fun updateStatus() {
        val isEnabledVal = switchEnabled.isChecked
        val statusText = if (isEnabledVal) "SYSTEM ACTIVE" else "ENGINE DISABLED"
        tvStatus.text = statusText
        tvStatus.setTextColor(
            if (isEnabledVal) ContextCompat.getColor(this, R.color.accent_green)
            else ContextCompat.getColor(this, R.color.accent_red)
        )
        
        // Update the status label color or something else if needed
        findViewById<TextView>(R.id.tv_status_label)?.setTextColor(
            if (isEnabledVal) ContextCompat.getColor(this, R.color.amoled_text_secondary)
            else ContextCompat.getColor(this, R.color.accent_red)
        )
    }

    private fun updateRegionsList() {
        regionsList.removeAllViews()

        if (regions.isEmpty()) {
            TextView(this).apply {
                text = "No blocked regions configured.\nTap 'Visual Setup' to add regions."
                setTextColor(getTextSecondaryColor())
                setPadding(32, 32, 32, 32)
                gravity = android.view.Gravity.CENTER
                regionsList.addView(this)
            }
            return
        }

        for (i in regions.indices) {
            addRegionView(i, regions[i])
        }
    }

    private fun addRegionView(index: Int, region: Region) {
        val view = layoutInflater.inflate(R.layout.item_region, regionsList, false)
        
        val tvTitle = view.findViewById<TextView>(R.id.tv_region_title)
        val tvCoords = view.findViewById<TextView>(R.id.tv_region_coords)
        val tvSize = view.findViewById<TextView>(R.id.tv_region_size)
        val btnRemove = view.findViewById<android.view.View>(R.id.btn_remove_region)
        
        val width = region.x2 - region.x1
        val height = region.y2 - region.y1
        
        tvTitle.text = "Region #${index + 1}"
        tvCoords.text = "(${region.x1}, ${region.y1}) - (${region.x2}, ${region.y2})"
        tvSize.text = "Size: ${width}x${height}"
        
        btnRemove.setOnClickListener { removeRegion(index) }
        
        regionsList.addView(view)
    }

    private fun showAddRegionDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Add Region Manually")
        builder.setMessage("Enter coordinates in normalized format (0.0 to 1.0): x1,y1,x2,y2\n\nExample: 0.1,0.1,0.2,0.3")

        val input = EditText(this).apply {
            hint = "0.1,0.1,0.2,0.3"
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
                Toast.makeText(this, "Invalid coordinates (must be 0.0-1.0 and x1<x2, y1<y2)", Toast.LENGTH_SHORT).show()
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
                parts[0].trim().toFloat().coerceIn(0f, 1f),
                parts[1].trim().toFloat().coerceIn(0f, 1f),
                parts[2].trim().toFloat().coerceIn(0f, 1f),
                parts[3].trim().toFloat().coerceIn(0f, 1f)
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
}
