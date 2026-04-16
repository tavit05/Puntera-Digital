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
 */
@Singleton
class SoundManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)

    /** Short beep for successful rapid scan */
    fun playSuccessBeep() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 100)
    }

    /** Error beep for validation failure */
    fun playErrorBeep() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 300)
    }

    /** Critical alert sound for Poka-Yoke violations */
    fun playCriticalAlert() {
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 500)
    }

    fun release() {
        toneGenerator.release()
    }
}
