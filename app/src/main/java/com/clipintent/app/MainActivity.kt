package com.clipintent.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.clipintent.app.HistoryDatabase.ClipEntry
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Main activity showing clipboard history with a toggle for the monitoring service.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var historyList: RecyclerView
    private lateinit var adapter: ClipHistoryAdapter
    private lateinit var db: HistoryDatabase
    private lateinit var serviceToggle: SwitchMaterial
    private lateinit var statusText: TextView
    private lateinit var statusIndicator: View
    private lateinit var searchBar: EditText
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = HistoryDatabase.getInstance(this)

        // Initialize views
        historyList = findViewById(R.id.history_list)
        serviceToggle = findViewById(R.id.service_toggle)
        statusText = findViewById(R.id.status_text)
        statusIndicator = findViewById(R.id.status_indicator)
        searchBar = findViewById(R.id.search_bar)
        emptyView = findViewById(R.id.empty_view)

        // Setup RecyclerView
        historyList.layoutManager = LinearLayoutManager(this)
        adapter = ClipHistoryAdapter(emptyList()) { entry -> deleteClipEntry(entry) }
        historyList.adapter = adapter

        // Setup service toggle
        serviceToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                requestNotificationPermissionIfNeeded()
                ClipboardService.startService(this@MainActivity)
            } else {
                ClipboardService.stopService(this@MainActivity)
            }
            updateStatusUI()
        }

        // Setup search bar
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isEmpty()) {
                    loadHistory()
                } else {
                    searchHistory(query)
                }
            }
        })

        // Toggle search bar visibility with a long press on the toolbar
        findViewById<View>(R.id.toolbar).setOnLongClickListener {
            searchBar.visibility = if (searchBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (searchBar.visibility == View.GONE) {
                searchBar.setText("")
                loadHistory()
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusUI()
        loadHistory()
    }

    /**
     * Update the status indicator and toggle state based on service running state.
     */
    private fun updateStatusUI() {
        val running = ClipboardService.isRunning()
        serviceToggle.isChecked = running
        if (running) {
            statusText.setText(R.string.service_enabled)
            statusIndicator.setBackgroundResource(R.drawable.circle_indicator_on)
        } else {
            statusText.setText(R.string.service_disabled)
            statusIndicator.setBackgroundResource(R.drawable.circle_indicator)
        }
    }

    /**
     * Load all clipboard history from the database.
     */
    private fun loadHistory() {
        val entries = db.getAllClips()
        adapter.updateData(entries)
        emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * Search clipboard history.
     */
    private fun searchHistory(query: String) {
        val entries = db.searchClips(query)
        adapter.updateData(entries)
        emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * Delete a single clip entry after confirmation.
     */
    private fun deleteClipEntry(entry: ClipEntry) {
        AlertDialog.Builder(this)
            .setTitle("Delete Entry")
            .setMessage("Delete this clipboard entry?")
            .setPositiveButton("Delete") { _, _ ->
                db.deleteClip(entry.id)
                loadHistory()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Request notification permission on Android 13+ (API 33+).
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }
}