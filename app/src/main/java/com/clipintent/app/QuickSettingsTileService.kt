package com.clipintent.app

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile service for toggling ClipIntent on/off.
 * The user can add this tile to their Quick Settings panel.
 */
class QuickSettingsTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (ClipboardService.isRunning()) {
            val intent = Intent(this, ClipboardService::class.java).apply {
                action = ClipboardService.ACTION_STOP
            }
            startService(intent)
        } else {
            val intent = Intent(this, ClipboardService::class.java).apply {
                action = ClipboardService.ACTION_START
            }
            startService(intent)
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        tile.state = if (ClipboardService.isRunning()) {
            Tile.STATE_ACTIVE
        } else {
            Tile.STATE_INACTIVE
        }
        tile.label = getString(R.string.quick_settings_label)
        tile.updateTile()
    }
}