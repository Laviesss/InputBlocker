package com.inputblocker.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class ProfileListActivity : AppCompatActivity() {

    companion object {
        private const val PROFILES_DIR = "/data/adb/modules/inputblocker/config/profiles"
    }

    private lateinit var profileContainer: LinearLayout
    private lateinit var btnCreateProfile: Button
    private lateinit var btnRefresh: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            val currentTheme = getSharedPreferences("InputBlockerPrefs", MODE_PRIVATE)
                .getInt("theme", ThemeManager.THEME_SYSTEM)
            val colors = ThemeManager.getThemeColors(this@ProfileListActivity, currentTheme)
            setBackgroundColor(colors.background)
        }

        val title = TextView(this).apply {
            text = "App Profiles"
            textSize = 24f
            val currentTheme = getSharedPreferences("InputBlockerPrefs", MODE_PRIVATE)
                .getInt("theme", ThemeManager.THEME_SYSTEM)
            val colors = ThemeManager.getThemeColors(this@ProfileListActivity, currentTheme)
            setTextColor(colors.textPrimary)
            setPadding(0, 0, 0, 32)
        }

        val description = TextView(this).apply {
            text = "Each profile is tied to an app package name. " +
                    "When that app is in the foreground, InputBlocker switches to its profile."
            textSize = 13f
            setPadding(0, 0, 0, 24)
            val colors = ThemeManager.getThemeColors(this@ProfileListActivity,
                getSharedPreferences("InputBlockerPrefs", MODE_PRIVATE)
                    .getInt("theme", ThemeManager.THEME_SYSTEM))
            setTextColor(colors.textSecondary)
        }

        // Button row
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        btnRefresh = Button(this).apply {
            text = "Refresh"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { refreshList() }
        }

        btnCreateProfile = Button(this).apply {
            text = "New Profile"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { showCreateProfileDialog() }
        }

        buttonLayout.addView(btnRefresh)
        buttonLayout.addView(btnCreateProfile)

        // Scrollable profile list
        profileContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scrollView = ScrollView(this).apply {
            addView(profileContainer)
        }

        root.addView(title)
        root.addView(description)
        root.addView(buttonLayout)
        root.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        setContentView(root)
        refreshList()
    }

    private fun refreshList() {
        profileContainer.removeAllViews()

        try {
            val profilesDir = File(PROFILES_DIR)
            if (!profilesDir.exists() || profilesDir.listFiles().isNullOrEmpty()) {
                showEmptyProfile("No profiles found. Create one by entering a package name.")
                return
            }

            val profileFiles = profilesDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".conf") }
                ?.sortedBy { it.name }
                ?: emptyList()

            if (profileFiles.isEmpty()) {
                showEmptyProfile("No profiles found. Create one by entering a package name.")
                return
            }

            for (file in profileFiles) {
                val profileName = file.name.removeSuffix(".conf")
                addProfileCard(profileName, file)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading profiles: ${e.message}", Toast.LENGTH_SHORT).show()
            showEmptyProfile("Error: ${e.message}")
        }
    }

    private fun addProfileCard(profileName: String, file: File) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(android.graphics.Color.parseColor("#1AFFFFFF"))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 12)
            layoutParams = lp
        }

        val nameText = TextView(this).apply {
            text = profileName
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            val colors = ThemeManager.getThemeColors(this@ProfileListActivity,
                getSharedPreferences("InputBlockerPrefs", MODE_PRIVATE)
                    .getInt("theme", ThemeManager.THEME_SYSTEM))
            setTextColor(colors.textPrimary)
        }

        val actionLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnLoad = Button(this).apply {
            text = "Load"
            textSize = 12f
            setOnClickListener {
                // Return profile name to MainActivity
                val intent = Intent().apply {
                    putExtra("profile_name", profileName)
                }
                setResult(RESULT_OK, intent)
                finish()
            }
        }

        val btnRename = Button(this).apply {
            text = "Rename"
            textSize = 12f
            setOnClickListener { showRenameDialog(profileName, file) }
        }

        val btnDelete = Button(this).apply {
            text = "Del"
            textSize = 12f
            setTextColor(android.graphics.Color.RED)
            setOnClickListener { showDeleteConfirmDialog(profileName, file) }
        }

        actionLayout.addView(btnLoad)
        actionLayout.addView(btnRename)
        actionLayout.addView(btnDelete)

        card.addView(nameText)
        card.addView(actionLayout)
        profileContainer.addView(card)
    }

    private fun showCreateProfileDialog() {
        val input = EditText(this).apply {
            hint = "com.example.app"
        }

        AlertDialog.Builder(this)
            .setTitle("New Profile")
            .setMessage("Enter the package name for this profile:")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val pkg = input.text.toString().trim()
                if (pkg.isEmpty()) {
                    Toast.makeText(this, "Package name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Create profile with default config (copy from default profile)
                try {
                    val profilesDir = File(PROFILES_DIR)
                    profilesDir.mkdirs()
                    val profileFile = File(profilesDir, "$pkg.conf")

                    if (profileFile.exists()) {
                        Toast.makeText(this, "Profile already exists for $pkg", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    val defaultConfig = File(profilesDir, "default.conf")
                    val content = if (defaultConfig.exists()) {
                        defaultConfig.readText()
                    } else {
                        "# InputBlocker Profile for $pkg\nenabled=1\nlsposed_mode=0\naccessibility_mode=0\n"
                    }

                    InputBlockerServiceManager.saveConfig(this, pkg, content)
                    Toast.makeText(this, "Profile created for $pkg", Toast.LENGTH_SHORT).show()
                    refreshList()
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to create profile: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameDialog(oldName: String, file: File) {
        val input = EditText(this).apply {
            setText(oldName)
        }

        AlertDialog.Builder(this)
            .setTitle("Rename Profile")
            .setMessage("Enter new package name:")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                try {
                    val newFile = File(file.parentFile, "$newName.conf")
                    if (newFile.exists()) {
                        Toast.makeText(this, "Profile $newName already exists", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    file.renameTo(newFile)
                    Toast.makeText(this, "Renamed to $newName", Toast.LENGTH_SHORT).show()
                    refreshList()
                } catch (e: Exception) {
                    Toast.makeText(this, "Rename failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmDialog(profileName: String, file: File) {
        AlertDialog.Builder(this)
            .setTitle("Delete Profile")
            .setMessage("Delete profile for $profileName?")
            .setPositiveButton("Delete") { _, _ ->
                try {
                    if (file.delete()) {
                        Toast.makeText(this, "Profile $profileName deleted", Toast.LENGTH_SHORT).show()
                        refreshList()
                    } else {
                        Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEmptyProfile(msg: String) {
        val emptyView = TextView(this).apply {
            text = msg
            textSize = 16f
            setTextColor(android.graphics.Color.GRAY)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 64, 0, 0)
        }
        profileContainer.addView(emptyView)
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }
}
