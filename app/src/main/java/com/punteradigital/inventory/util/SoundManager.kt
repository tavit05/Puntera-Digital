package com.punteradigital.inventory.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.media.ToneGenerator
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages audio feedback for scan operations.
 * - Beep sound on successful scan (burst mode)
 * - Error tone on validation failure
 * ToneGenerator is created lazily and released when not in use to avoid
 * holding audio hardware resources permanently.
 */
@Singleton
class SoundManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Lazily allocated — only holds audio hardware during active scanner sessions
    private var _toneGenerator: ToneGenerator? = null
    private val toneGenerator: ToneGenerator
        get() {
            if (_toneGenerator == null) {
                _toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            }
            return _toneGenerator!!
        }

    /** Short beep for successful rapid scan */
    fun playSuccessBeep() {
        try { toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 100) }
        catch (e: Exception) { /* Audio may be unavailable on some devices */ }
    }

    /** Error beep for validation failure */
    fun playErrorBeep() {
        try { toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 300) }
        catch (e: Exception) { /* Audio may be unavailable on some devices */ }
    }

    /** Critical alert sound for Poka-Yoke violations */
    fun playCriticalAlert() {
        try { toneGenerator.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 500) }
        catch (e: Exception) { /* Audio may be unavailable on some devices */ }
    }

    /** Release audio hardware — call when scanner session ends */
    fun release() {
        _toneGenerator?.release()
        _toneGenerator = null
    }
}
