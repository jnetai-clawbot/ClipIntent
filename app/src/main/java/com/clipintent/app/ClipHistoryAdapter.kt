package com.clipintent.app

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.clipintent.app.ContentAnalyzer.ContentType
import com.clipintent.app.HistoryDatabase.ClipEntry
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView adapter for displaying clipboard history entries.
 */
class ClipHistoryAdapter(
    private var entries: List<ClipEntry>,
    private val onDeleteClick: (ClipEntry) -> Unit
) : RecyclerView.Adapter<ClipHistoryAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_clip, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        holder.bind(entry)
    }

    override fun getItemCount(): Int = entries.size

    /**
     * Update the adapter's data set.
     */
    fun updateData(newEntries: List<ClipEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val contentText: TextView = itemView.findViewById(R.id.content_text)
        private val actionContainer: View = itemView.findViewById(R.id.action_container)
        private val actionChip: Chip = itemView.findViewById(R.id.action_chip)
        private val timestampText: TextView = itemView.findViewById(R.id.timestamp_text)

        fun bind(entry: ClipEntry) {
            contentText.text = entry.content
            timestampText.text = dateFormat.format(Date(entry.timestamp))

            // Show action chip based on content type
            try {
                val type = ContentType.valueOf(entry.type)
                actionChip.text = type.actionLabel
                actionContainer.visibility = View.VISIBLE

                // Set chip color based on type
                val chipColor = when (type) {
                    ContentType.URL -> R.color.chip_url
                    ContentType.PHONE -> R.color.chip_phone
                    ContentType.EMAIL -> R.color.chip_email
                    ContentType.ADDRESS -> R.color.chip_address
                    ContentType.TRACKING -> R.color.chip_tracking
                    ContentType.CRYPTO -> R.color.chip_crypto
                    ContentType.TEXT -> R.color.chip_text
                }
                actionChip.setChipBackgroundColorResource(chipColor)

                // Set click action on chip
                actionChip.setOnClickListener {
                    performAction(itemView.context, entry.content, type)
                }
            } catch (e: IllegalArgumentException) {
                actionContainer.visibility = View.GONE
            }

            // Long click to delete
            itemView.setOnLongClickListener {
                onDeleteClick(entry)
                true
            }
        }

        private fun performAction(context: android.content.Context, content: String, type: ContentType) {
            val uri = ContentAnalyzer.getActionUri(content, type)
            if (uri != null) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                context.startActivity(intent)
            } else {
                when (type) {
                    ContentType.ADDRESS -> {
                        val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(content)}"))
                        context.startActivity(mapIntent)
                    }
                    ContentType.TRACKING -> {
                        val searchIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com/search?q=${Uri.encode("track package $content")}")
                        )
                        context.startActivity(searchIntent)
                    }
                    else -> {
                        // Copy to clipboard
                        val clip = android.content.ClipData.newPlainText("clip", content)
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                        cm.setPrimaryClip(clip)
                    }
                }
            }
        }
    }
}