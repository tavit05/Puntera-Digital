package com.punteradigital.inventory.domain.model

/**
 * Origin/owner of the merchandise.
 * Controls the visual theme and UUID prefix.
 */
enum class Origin(val prefix: String, val displayName: String) {
    FOOT_SAFE("FS", "Foot Safe"),
    SAFETY("SF", "Safety");

    companion object {
        /**
         * Detects origin from a UUID prefix.
         * FS-xxxx → FOOT_SAFE
         * SF-xxxx → SAFETY
         */
        fun fromUuid(uuid: String): Origin? = when {
            uuid.startsWith("FS-") -> FOOT_SAFE
            uuid.startsWith("SF-") -> SAFETY
            else -> null
        }
    }
}
