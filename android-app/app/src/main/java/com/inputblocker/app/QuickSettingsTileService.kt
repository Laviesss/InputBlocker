package com.inputblocker.app

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class QuickSettingsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        
        val prefs = getSharedPreferences("InputBlockerPrefs", MODE_PRIVATE)
        val currentStatus = prefs.getBoolean("enabled", true)
        val newStatus = !currentStatus
        
        prefs.edit().putBoolean("enabled", newStatus).apply()
        updateTileState()
        
        val configPath = InputBlockerServiceManager.getConfigFile(this, "default")
        val cmd = if (newStatus) "enabled=1" else "enabled=0"
        InputBlockerServiceManager.runRootCommand("sed -i 's/^enabled=.*/$cmd/' $configPath")
        
        val intent = Intent("com.inputblocker.RELOAD")
        intent.setPackage(packageName)
        sendBroadcast(intent)
        
        Toast.makeText(this, "Blocking ${if (newStatus) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val prefs = getSharedPreferences("InputBlockerPrefs", MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("enabled", true)
        
        tile.label = "InputBlocker"
        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        
        tile.updateTile()
    }
}
