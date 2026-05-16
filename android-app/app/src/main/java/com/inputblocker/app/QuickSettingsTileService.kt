package com.inputblocker.app

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast

class QuickSettingsTileService : TileService() {

    companion object {
        private const val TAG = "InputBlocker-Tile"
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        
        val prefs = getSharedPreferences("InputBlockerPrefs", MODE_PRIVATE)
        val currentStatus = prefs.getBoolean("enabled", true)
        val newStatus = !currentStatus
        
        // 1. Update preferences
        prefs.edit().putBoolean("enabled", newStatus).apply()
        
        // 2. Update the tile UI
        updateTileState()
        
        // 3. Sync with root module via shell command
        // We use a root command to update the config file directly for immediate effect
        val cmd = if (newStatus) "enabled=1" else "enabled=0"
        InputBlockerServiceManager.runRootCommand("sed -i 's/^enabled=.*/$cmd/' /data/adb/modules/inputblocker/config/blocked_regions.conf")
        
        // 4. Notify the system to reload the config
        InputBlockerServiceManager.runRootCommand("am broadcast -a com.inputblocker.RELOAD")
        
        Toast.makeText(this, "Blocking ${if (newStatus) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
    }

    private fun updateTileState() {
        val tile = qsTile
        val prefs = getSharedPreferences("InputBlockerPrefs", MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("enabled", true)
        
        tile.label = "InputBlocker"
        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        
        tile.updateTile()
    }
}
