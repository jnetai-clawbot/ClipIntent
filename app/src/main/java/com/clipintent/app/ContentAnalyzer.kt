package com.clipintent.app

/**
 * ContentAnalyzer uses regex patterns to detect the type of clipboard content.
 * All analysis is performed locally — no internet permission required.
 */
object ContentAnalyzer {

    /**
     * Enum representing the detected content type, with associated icon/action metadata.
     */
    enum class ContentType(val label: String, val actionLabel: String) {
        URL("URL", "Open in Browser"),
        PHONE("Phone Number", "Call"),
        EMAIL("Email Address", "Compose Email"),
        ADDRESS("Address/Location", "Open in Maps"),
        TRACKING("Tracking Number", "Track Package"),
        CRYPTO("Crypto Address", "View Address"),
        TEXT("Plain Text", "Copy")
    }

    // Regex patterns for each content type
    private val URL_PATTERN = Regex(
        "^(https?://|www\\.)[\\w./?=&%-]+(\\.[a-zA-Z]{2,})(/\\S*)?\$",
        RegexOption.IGNORE_CASE
    )

    // Phone numbers: US-style (123) 456-7890, international +1234567890, or simple digit strings of 7-15 digits
    private val PHONE_PATTERN = Regex(
        """^(\+?\d{1,3}[-.\s]?)?(\(?\d{3}\)?[-.\s]?)?\d{3}[-.\s]?\d{4}(?:\s?(?:ext|x|xtn)\s?\d{1,5})?$"""
    )

    // Email addresses
    private val EMAIL_PATTERN = Regex(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    )

    // Simple address patterns (contains number + street/road/ave etc. or city/state/zip)
    private val ADDRESS_PATTERN = Regex(
        """(\d+\s+[A-Za-z\s]+(?:street|st|road|rd|avenue|ave|drive|dr|lane|ln|boulevard|blvd|way|court|ct|circle|cir|place|pl|square|sq))""",
        RegexOption.IGNORE_CASE
    )

    // Tracking numbers: UPS (1Z...), FedEx (digits), USPS (alphanumeric), generic
    private val TRACKING_PATTERN = Regex(
        """^(1Z\s*[\da-zA-Z]{12,18}|\d{12,22}|[A-Z]{2}\d{9}US|[A-Za-z0-9]{10,22})$"""
    )

    // Crypto addresses: Bitcoin (1,3,bc1), Ethereum (0x), etc.
    private val CRYPTO_PATTERN = Regex(
        """^(0x[a-fA-F0-9]{40}|[13][a-km-zA-HJ-NP-Z1-9]{25,34}|bc1[a-zA-HJ-NP-Z0-9]{25,39}|[LM][a-km-zA-HJ-NP-Z1-9]{26,33})$"""
    )

    /**
     * Analyze clipboard text and determine its content type.
     * Priority order: URL -> Phone -> Email -> Address -> Tracking -> Crypto -> Text
     */
    fun analyze(text: String): ContentType {
        val trimmed = text.trim()

        if (trimmed.isEmpty()) return ContentType.TEXT

        return when {
            URL_PATTERN.matches(trimmed) -> ContentType.URL
            isPhoneNumber(trimmed) -> ContentType.PHONE
            EMAIL_PATTERN.matches(trimmed) -> ContentType.EMAIL
            ADDRESS_PATTERN.containsMatchIn(trimmed) -> ContentType.ADDRESS
            TRACKING_PATTERN.matches(trimmed.replace(" ", "")) -> ContentType.TRACKING
            CRYPTO_PATTERN.matches(trimmed) -> ContentType.CRYPTO
            else -> ContentType.TEXT
        }
    }

    /**
     * Heuristic to validate phone numbers more carefully:
     * Must contain at least 7 digits, at most 15 digits (after stripping separators).
     */
    private fun isPhoneNumber(input: String): Boolean {
        val digitsOnly = input.replace("""[\s\-\.\(\)]""".toRegex(), "")
        // Must match the general phone pattern and have valid digit count
        return PHONE_PATTERN.matches(input) &&
                digitsOnly.length in 7..15 &&
                digitsOnly.all { it.isDigit() || it == '+' }
    }

    /**
     * Return an appropriate action URI for the given text and content type.
     * Returns null if no actionable URI can be constructed.
     */
    fun getActionUri(text: String, type: ContentType): String? {
        val trimmed = text.trim()
        return when (type) {
            ContentType.URL -> {
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
                else "http://$trimmed"
            }
            ContentType.PHONE -> "tel:${trimmed.replace("""[\s\-\.\(\)]""".toRegex(), "")}"
            ContentType.EMAIL -> "mailto:$trimmed"
            ContentType.ADDRESS -> null // Maps intent requires geo: or query
            ContentType.TRACKING -> null // Open browser search dynamically
            ContentType.CRYPTO -> null // View on block explorer (needs internet)
            ContentType.TEXT -> null
        }
    }
}