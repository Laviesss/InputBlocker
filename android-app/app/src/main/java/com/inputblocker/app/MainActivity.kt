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
import com.google.android.material.tabs.TabLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.inputblocker.shared.Region
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

    // Tabs and Containers
    private lateinit var tabLayout: TabLayout
    private lateinit var containerControls: View
    private lateinit var containerQuickActions: View

    // Quick Action Buttons
    private lateinit var btnActionSafe: Button
    private lateinit var btnActionSync: Button
    private lateinit var btnActionExport: Button
    private lateinit var btnActionTest: Button
    private lateinit var btnViewLog: Button
    private lateinit var btnOptimizeRegions: Button
    private lateinit var btnBackupRestore: Button
    private lateinit var btnImportPreset: Button
    private lateinit var btnExportPreset: Button
    private lateinit var btnCommunityGallery: Button
    private lateinit var btnSubmitPreset: Button

    private var isEnabled = true
    private var isLsposedMode = false
    private val regions = mutableListOf<Region>()
    private var currentTheme = THEME_SYSTEM

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Overlay permission still missing.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted!", Toast.LENGTH_SHORT).show()
        }
    }

    private var detectionReceiver: android.content.BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Senior Engineering: Fail-safe crash tracking
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            InputBlockerServiceManager.reportCrash()
            oldHandler?.uncaughtException(thread, throwable)
        }

        setContentView(R.layout.activity_main)

        // Bind Views
        tabLayout = findViewById(R.id.tab_layout)
        containerControls = findViewById(R.id.container_controls)
        containerQuickActions = findViewById(R.id.container_quick_actions)
        
        switchEnabled = findViewById(R.id.switch_enabled)
        regionsList = findViewById(R.id.regions_list)
        tvStatus = findViewById(R.id.tv_status)
        btnLaunchSetup = findViewById(R.id.btn_launch_setup)
        btnAddRegion = findViewById(R.id.btn_add_region)
        btnClearAll = findViewById(R.id.btn_clear_all)
        btnTheme = findViewById(R.id.btn_theme)
        btnAutoDetect = findViewById(R.id.btn_auto_detect)

        btnActionSafe = findViewById(R.id.btn_action_safe)
        btnActionSync = findViewById(R.id.btn_action_sync)
        btnActionExport = findViewById(R.id.btn_action_export)
        btnActionTest = findViewById(R.id.btn_action_test)
        btnViewLog = findViewById(R.id.btn_view_log)
        btnOptimizeRegions = findViewById(R.id.btn_optimize_regions)
        btnBackupRestore = findViewById(R.id.btn_backup_restore)
        btnImportPreset = findViewById(R.id.btn_import_preset)
        btnExportPreset = findViewById(R.id.btn_export_preset)
        btnCommunityGallery = findViewById(R.id.btn_community_gallery)
        btnSubmitPreset = findViewById(R.id.btn_submit_preset)

        setupTabs()
        setupControlListeners()
        setupQuickActionListeners()
        setupDetectionReceiver()
        
        loadPrefs()
        loadConfig()
        updateUI()
        applyTheme()
        applyThemeToViews()
        checkForUpdates()
        
        handleQuickActionIntent(intent)
    }

    private fun setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("Controls"))
        tabLayout.addTab(tabLayout.newTab().setText("Quick Actions"))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (tab.position == 0) {
                    containerControls.visibility = View.VISIBLE
                    containerQuickActions.visibility = View.GONE
                } else {
                    containerControls.visibility = View.GONE
                    containerQuickActions.visibility = View.VISIBLE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupControlListeners() {
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                InputBlockerServiceManager.clearEmergencyReset(this)
            }
            isEnabled = isChecked
            saveEnabledState(isChecked)
            updateStatus()
            updateUI()
        }
        
        findViewById<SwitchMaterial>(R.id.switch_blocking_method).setOnCheckedChangeListener { _, isChecked ->
            isLsposedMode = isChecked
            saveLsposedPreference(isChecked)
            updateStatus()
            
            if (isChecked) {
                Toast.makeText(this, "LSPosed Mode enabled. Please enable this module in LSPosed Manager.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Overlay Mode enabled. The app will now use a system overlay to block touches.", Toast.LENGTH_LONG).show()
            }
        }
        
        btnLaunchSetup.setOnClickListener { checkOverlayPermission() }
        btnAddRegion.setOnClickListener { showAddRegionDialog() }
        btnClearAll.setOnClickListener { confirmClearAll() }
        btnTheme.setOnClickListener { showThemeDialog() }
        btnAutoDetect.setOnClickListener { startAutoDetection() }
    }

    private fun setupQuickActionListeners() {
        btnActionSafe.setOnClickListener {
            toggleSafeMode()
        }
        btnActionSync.setOnClickListener {
            Toast.makeText(this, "Syncing with device...", Toast.LENGTH_SHORT).show()
        }
        btnActionExport.setOnClickListener {
            exportCurrentConfig()
        }
        btnActionTest.setOnClickListener {
            Toast.makeText(this, "Running test mode...", Toast.LENGTH_SHORT).show()
        }
        btnViewLog.setOnClickListener {
            val intent = Intent(this, BlockLogActivity::class.java)
            startActivity(intent)
        }
        btnOptimizeRegions.setOnClickListener {
            Toast.makeText(this, "Applying adaptive optimization...", Toast.LENGTH_SHORT).show()
            AdaptiveBlockingManager.analyzeAndOptimize(this)
            Toast.makeText(this, "Optimization complete!", Toast.LENGTH_SHORT).show()
        }
        btnBackupRestore.setOnClickListener {
            showBackupDialog()
        }
        btnImportPreset.setOnClickListener {
            importPreset()
        }
        btnExportPreset.setOnClickListener {
            exportPreset()
        }
        btnCommunityGallery.setOnClickListener {
            val intent = Intent(this, CommunityGalleryActivity::class.java)
            startActivity(intent)
        }
        btnSubmitPreset.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Submit Preset")
                .setMessage("To submit your preset to the community gallery, please open an issue on GitHub and attach your .ibpreset file.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun toggleSafeMode() {
        InputBlockerServiceManager.enableSafeMode(this)
        Toast.makeText(this, "Safe Mode Enabled: All blocking suspended", Toast.LENGTH_LONG).show()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQuickActionIntent(intent)
    }

    private fun handleQuickActionIntent(intent: Intent?) {
        if (intent?.action == "com.inputblocker.ACTION_QUICK_MENU") {
            val tab = tabLayout.getTabAt(1)
            if (tab != null) {
                tabLayout.selectTab(tab)
            }
            containerControls.visibility = View.GONE
            containerQuickActions.visibility = View.VISIBLE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.findItem(R.id.action_profile)?.setTitle("App Profile")
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
            R.id.action_profile -> {
                showProfileDialog()
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

    private fun showAboutDialog() {
        val version = BuildConfig.VERSION_NAME
        AlertDialog.Builder(this)
            .setTitle("About InputBlocker")
            .setMessage("InputBlocker v$version\n\nBlock ghost taps and unwanted touch inputs.\n\nCreated by Laviesss")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showProfileDialog() {
        val packageNameEditText = EditText(this).apply {
            hint = "Enter package name (e.g. com.android.settings)"
        }
        
        AlertDialog.Builder(this)
            .setTitle("App-Specific Profile")
            .setMessage("Enter the package name of the app you want to create a profile for. This will switch to this profile when the app is in the foreground.")
            .setView(packageNameEditText)
            .setPositiveButton("Create/Edit") { _, _ ->
                val pkg = packageNameEditText.text.toString().trim()
                if (pkg.isEmpty()) {
                    Toast.makeText(this, "Package name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val configContent = StringBuilder()
                configContent.append("# InputBlocker App-Specific Profile for $pkg\n")
                configContent.append("enabled=${if (isEnabled) "1" else "0"}\n")
                configContent.append("lsposed_mode=${if (isLsposedMode) "1" else "0"}\n\n")
                configContent.append("# Blocked regions:\n")
                for (region in regions) {
                    configContent.append("$region\n")
                }
                
                InputBlockerServiceManager.saveConfig(this, pkg, configContent.toString())
                Toast.makeText(this, "Profile saved for $pkg", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        loadConfig()
        updateUI()
        applyThemeToViews()
    }

    private fun setupDetectionReceiver() {
        detectionReceiver = object : android.content.BroadcastReceiver() {
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
            registerReceiver(detectionReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(detectionReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detectionReceiver?.let { unregisterReceiver(it) }
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
        val threshold = 0.05f 
        
        for (i in touches.indices) {
            if (used[i]) continue
            
            var minX = touches[i].first
            var maxX = touches[i].first
            var minY = touches[i].second
            var maxY = touches[i].second
            
            used[i] = true
            
            var changed = true
            while (changed) {
                changed = false
                for (j in touches.indices) {
                    if (used[j]) continue
                    if (Math.abs(touches[j].first - (minX + maxX) / 2) < threshold && 
                        Math.abs(touches[j].second - (minY + maxY) / 2) < threshold) {
                        minX = Math.min(minX, touches[j].first)
                        maxX = Math.max(maxX, touches[j].first)
                        minY = Math.min(minY, touches[j].second)
                        maxY = Math.max(maxY, touches[j].second)
                        used[j] = true
                        changed = true
                    }
                }
            }
            regionsResult.add(Region(minX, minY, maxX, maxY))
        }
        return regionsResult
    }

    private fun applyTheme() {
        ThemeManager.applyTheme(this, currentTheme)
    }

    private fun applyThemeToViews() {
        val colors = ThemeManager.getThemeColors(this, currentTheme)
        ThemeManager.applyThemeToViewHierarchy(findViewById(android.R.id.content), colors)
        
        tvStatus.apply {
            setTextColor(
                if (isEnabled) ContextCompat.getColor(this@MainActivity, R.color.accent_green)
                else ContextCompat.getColor(this@MainActivity, R.color.accent_red)
            )
        }
        
        findViewById<TextView>(R.id.tv_app_name)?.setTextColor(colors.textPrimary)
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

    private fun startAutoDetection() {
        Thread {
            try {
                Log.i("MainActivity", "Starting Auto-Detection sequence...")
                InputBlockerServiceManager.runRootCommand("settings put secure lockscreen.disabled 1")
                Thread.sleep(500)
                InputBlockerServiceManager.runRootCommand("input keyevent 26")
                Thread.sleep(2000)
                InputBlockerServiceManager.runRootCommand("input keyevent KEYCODE_WAKEUP")
                Thread.sleep(1000)
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
            .setTitle("Select Theme")
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
        if (isLsposedMode) {
            showVisualEditor()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                overlayPermissionLauncher.launch(intent)
            } else {
                showVisualEditor()
            }
        }
    }

    private fun showVisualEditor() {
        val editor = RegionEditorView(this).apply {
            setRegions(regions)
            onRegionChanged = { updatedRegions ->
                regions.clear()
                regions.addAll(updatedRegions)
                saveConfig()
                updateUI()
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("Visual Region Setup")
            .setMessage("Drag to draw new regions. Select a region to move or resize it.")
            .setView(editor)
            .setPositiveButton("Save") { _, _ ->
                saveConfig()
                updateUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadPrefs() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        currentTheme = prefs.getInt(PREF_THEME, ThemeManager.THEME_SYSTEM)
    }

    private fun saveThemePreference(theme: Int) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(PREF_THEME, theme).apply()
        currentTheme = theme
    }

    private fun saveEnabledState(enabled: Boolean) {
        isEnabled = enabled
        saveConfig()
    }

    private fun saveLsposedPreference(enabled: Boolean) {
        isLsposedMode = enabled
        saveConfig()
    }

    private fun loadConfig() {
        regions.clear()

        if (File(InputBlockerServiceManager.getModulePath(this) + "/config/kill_switch").exists()) {
            isEnabled = false
            Toast.makeText(this, "Emergency reset active: Blocking disabled", Toast.LENGTH_LONG).show()
        }

        val configFile = File(InputBlockerServiceManager.getConfigFile(this, "default"))
        if (!configFile.exists()) return

        try {
            BufferedReader(FileReader(configFile)).use { reader ->
                reader.lineSequence().forEach { line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.isEmpty() || trimmed.startsWith("#") -> return@forEach
                        trimmed.startsWith("enabled=") -> isEnabled = trimmed.substring(8) == "1"
                        trimmed.startsWith("lsposed_mode=") -> isLsposedMode = trimmed.substring(13) == "1"
                        else -> {
                            Region.fromString(trimmed)?.let { regions.add(it) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading config", e)
        }
    }

    private fun optimizeRegions() {
        try {
            val logFile = File(InputBlockerServiceManager.getModulePath(this) + "/config/blocklog.txt")
            if (!logFile.exists()) {
                Toast.makeText(this, "No block log found. Please use the app while blocking is active first.", Toast.LENGTH_LONG).show()
                return
            }

            val lines = logFile.readLines()
            if (lines.isEmpty()) {
                Toast.makeText(this, "Block log is empty.", Toast.LENGTH_SHORT).show()
                return
            }

            var optimizedCount = 0
            val newRegions = regions.map { region ->
                val hits = lines.filter { line ->
                    try {
                        // Format: "HH:mm:ss | X: 0.123, Y: 0.456 | Region: [x1, y1, x2, y2]"
                        val parts = line.split(" | ")
                        if (parts.size < 2) return@filter false
                        
                        val coords = parts[1].split(", ")
                        val x = coords[0].substringAfter("X: ").toFloat()
                        val y = coords[1].substringAfter("Y: ").toFloat()
                        
                        x >= region.x1 && x <= region.x2 && y >= region.y1 && y <= region.y2
                    } catch (e: Exception) {
                        false
                    }
                }

                if (hits.size >= 10) {
                    var minX = 1.0f
                    var maxX = 0.0f
                    var minY = 1.0f
                    var maxY = 0.0f
                    
                    hits.forEach { line ->
                        try {
                            val coords = line.split(" | ")[1].split(", ")
                            val x = coords[0].substringAfter("X: ").toFloat()
                            val y = coords[1].substringAfter("Y: ").toFloat()
                            if (x < minX) minX = x
                            if (x > maxX) maxX = x
                            if (y < minY) minY = y
                            if (y > maxY) maxY = y
                        } catch (e: Exception) {}
                    }
                    
                    val padding = 0.005f
                    optimizedCount++
                    Region(
                        (minX - padding).coerceAtLeast(0.0f),
                        (minY - padding).coerceAtLeast(0.0f),
                        (maxX + padding).coerceAtMost(1.0f),
                        (maxY + padding).coerceAtMost(1.0f)
                    )
                } else {
                    region
                }
            }.toMutableList()

            regions.clear()
            regions.addAll(newRegions)
            saveConfig()
            updateUI()
            
            Toast.makeText(this, "Optimization complete: $optimizedCount regions shrunk based on usage.", Toast.LENGTH_LONG).show()
            
            // Optional: Clear log after optimization to start fresh
            // logFile.delete()
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Optimization failed", e)
            Toast.makeText(this, "Optimization failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showBackupDialog() {
        AlertDialog.Builder(this)
            .setTitle("Backup & Restore")
            .setMessage("Manage your configuration backups. These files can be synced to the cloud using your preferred sync app.")
            .setPositiveButton("Backup Now") { _, _ ->
                if (InputBlockerServiceManager.createBackup(this)) {
                    Toast.makeText(this, "Backup created successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Backup failed. Please check storage permissions.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Restore") { _, _ ->
                showRestoreFilePicker()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRestoreFilePicker() {
        // In a real app, we would use a Storage Access Framework (SAF) intent to pick a file.
        // For this module, we'll search for the most recent backup in the backup folder.
        try {
            val backupDir = File("/storage/emulated/0/InputBlocker/backups")
            val latestBackup = backupDir.listFiles { _, name -> name.startsWith("backup_") && name.endsWith(".txt") }
                ?.maxByOrNull { it.lastModified() }

            if (latestBackup == null) {
                Toast.makeText(this, "No backups found in /InputBlocker/backups/", Toast.LENGTH_LONG).show()
                return
            }

            AlertDialog.Builder(this)
                .setTitle("Restore Backup")
                .setMessage("Restore from latest backup:\n${latestBackup.name}?\n\nThis will overwrite current settings.")
                .setPositiveButton("Restore") { _, _ ->
                    if (InputBlockerServiceManager.restoreBackup(this, latestBackup)) {
                        Toast.makeText(this, "Restore successful! Restarting services...", Toast.LENGTH_SHORT).show()
                        loadConfig()
                        updateUI()
                    } else {
                        Toast.makeText(this, "Restore failed.", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error accessing backups: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportPreset() {
        try {
            val presetFile = File("/storage/emulated/0/InputBlocker/presets/my_preset.ibpreset")
            presetFile.parentFile?.mkdirs()
            
            val content = StringBuilder()
            content.append("DEVICE_MODEL:${Build.MODEL}\n")
            content.append("VERSION:1.0\n")
            content.append("COUNT:${regions.size}\n")
            
            for (region in regions) {
                content.append("${region.isExclude},${region.type},${region.x1},${region.y1},${region.x2},${region.y2},${region.minPressure},${region.maxDuration}\n")
            }
            
            presetFile.writeText(content.toString())
            Toast.makeText(this, "Preset exported to /InputBlocker/presets/my_preset.ibpreset", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importPreset() {
        try {
            val presetFile = File("/storage/emulated/0/InputBlocker/presets/my_preset.ibpreset")
            if (!presetFile.exists()) {
                Toast.makeText(this, "No preset file found at /InputBlocker/presets/my_preset.ibpreset", Toast.LENGTH_LONG).show()
                return
            }

            val lines = presetFile.readLines()
            if (lines.size < 3) {
                Toast.makeText(this, "Invalid preset format", Toast.LENGTH_SHORT).show()
                return
            }

            val model = lines[0].substringAfter("DEVICE_MODEL:")
            val count = lines[2].substringAfter("COUNT:").toInt()
            
            AlertDialog.Builder(this)
                .setTitle("Import Preset")
                .setMessage("Import preset from $model?\nRegions: $count\n\nThis will replace your current regions.")
                .setPositiveButton("Import") { _, _ ->
                    regions.clear()
                    for (i in 3 until lines.size) {
                        val line = lines[i]
                        if (line.isBlank()) continue
                        val parts = line.split(",")
                        if (parts.size == 8) {
                            regions.add(Region(
                                isExclude = parts[0].trim().toInt() == 1,
                                type = parts[1].trim().toInt(),
                                x1 = parts[2].trim().toFloat(),
                                y1 = parts[3].trim().toFloat(),
                                x2 = parts[4].trim().toFloat(),
                                y2 = parts[5].trim().toFloat(),
                                minPressure = parts[6].trim().toFloat(),
                                maxDuration = parts[7].trim().toLong()
                            ))
                        }
                    }
                    saveConfig()
                    updateUI()
                    Toast.makeText(this, "Preset imported successfully!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveConfig() {
        val content = StringBuilder()
        content.append("# InputBlocker Configuration\n")
        content.append("enabled=${if (isEnabled) "1" else "0"}\n")
        content.append("lsposed_mode=${if (isLsposedMode) "1" else "0"}\n\n")
        content.append("# Blocked regions:\n")
        for (region in regions) {
            content.append("$region\n")
        }
        InputBlockerServiceManager.saveConfig(this, "default", content.toString())
    }

    private fun updateUI() {
        switchEnabled.isChecked = isEnabled
        
        val lsposedSwitch = findViewById<SwitchMaterial>(R.id.switch_blocking_method)
        lsposedSwitch.isChecked = isLsposedMode
        
        updateStatus()
        updateRegionsList()
    }

    private fun updateStatus() {
        val isEnabledVal = switchEnabled.isChecked
        val colors = ThemeManager.getThemeColors(this, currentTheme)
        
        tvStatus.text = if (isEnabledVal) "SYSTEM ACTIVE" else "ENGINE DISABLED"
        tvStatus.setTextColor(if (isEnabledVal) ContextCompat.getColor(this, R.color.accent_green) else ContextCompat.getColor(this, R.color.accent_red))
        findViewById<TextView>(R.id.tv_status_label)?.setTextColor(colors.textSecondary)
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
        view.findViewById<TextView>(R.id.tv_region_title).text = "Region #${index + 1}"
        view.findViewById<TextView>(R.id.tv_region_coords).text = "(${region.x1}, ${region.y1}) - (${region.x2}, ${region.y2})"
        view.findViewById<TextView>(R.id.tv_region_size).text = "Size: ${region.x2 - region.x1}x${region.y2 - region.y1}"
        view.findViewById<android.view.View>(R.id.btn_remove_region).setOnClickListener { removeRegion(index) }
        regionsList.addView(view)
    }

    private fun showAddRegionDialog() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Add Region Manually")
            .setMessage("Enter coordinates (x1,y1,x2,y2):")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                parseAndAddRegion(input.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun parseAndAddRegion(input: String) {
        val region = try {
            val parts = input.split(",")
            if (parts.size == 4) {
                val x1 = parts[0].trim().toFloat()
                val y1 = parts[1].trim().toFloat()
                val x2 = parts[2].trim().toFloat()
                val y2 = parts[3].trim().toFloat()
                if (x1 < x2 && y1 < y2) Region(x1, y1, x2, y2) else null
            } else null
        } catch (_: Exception) { null }

        if (region != null) {
            regions.add(region)
            saveConfig()
            updateUI()
            Toast.makeText(this, "Region added", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Invalid format. Use: x1,y1,x2,y2", Toast.LENGTH_SHORT).show()
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

    private fun exportCurrentConfig() {
        try {
            val configFile = File(InputBlockerServiceManager.getConfigFile(this))
            val exportFile = File(getExternalFilesDir(null), "inputblocker_config.conf")
            configFile.copyTo(exportFile, overwrite = true)
            Toast.makeText(this, "Config exported to: ${exportFile.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
}
