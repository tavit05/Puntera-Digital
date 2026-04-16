package com.punteradigital.inventory.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.punteradigital.inventory.domain.model.Origin

// ═══════════════════════════════════════════════════════════════
// Foot Safe Dark Theme — Kinetic Architect
// ═══════════════════════════════════════════════════════════════
private val FootSafeColorScheme = darkColorScheme(
    primary = KineticPrimaryContainer,
    onPrimary = KineticOnPrimary,
    primaryContainer = KineticPrimaryContainer,
    onPrimaryContainer = KineticOnPrimaryContainer,
    secondary = KineticSecondary,
    onSecondary = Color.White,
    secondaryContainer = KineticSecondaryContainer,
    onSecondaryContainer = Color.White,
    background = KineticSurfaceDim,
    onBackground = KineticOnSurface,
    surface = KineticSurface,
    onSurface = KineticOnSurface,
    surfaceVariant = KineticSurfaceContainerHigh,
    onSurfaceVariant = KineticOnSurfaceVariant,
    surfaceContainer = KineticSurfaceContainer,
    surfaceContainerLow = KineticSurfaceContainerLow,
    surfaceContainerHigh = KineticSurfaceContainerHigh,
    surfaceContainerHighest = KineticSurfaceContainerHighest,
    surfaceContainerLowest = KineticSurfaceContainerLowest,
    outline = KineticOutline,
    outlineVariant = KineticOutlineVariant,
    error = KineticError,
    onError = Color.Black,
    tertiary = KineticSuccess,
    onTertiary = Color.Black
)

// ═══════════════════════════════════════════════════════════════
// Foot Safe Light Theme — Kinetic Architect
// ═══════════════════════════════════════════════════════════════
private val FootSafeLightColorScheme = lightColorScheme(
    primary = KineticLightPrimary,
    onPrimary = KineticLightOnPrimary,
    primaryContainer = KineticLightPrimaryContainer,
    onPrimaryContainer = KineticLightOnPrimaryContainer,
    secondary = KineticLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = KineticLightSecondaryContainer,
    onSecondaryContainer = Color(0xFF001C3B),
    background = KineticLightSurfaceDim,
    onBackground = KineticLightOnSurface,
    surface = KineticLightSurface,
    onSurface = KineticLightOnSurface,
    surfaceVariant = KineticLightSurfaceContainerHigh,
    onSurfaceVariant = KineticLightOnSurfaceVariant,
    surfaceContainer = KineticLightSurfaceContainer,
    surfaceContainerLow = KineticLightSurfaceContainerLow,
    surfaceContainerHigh = KineticLightSurfaceContainerHigh,
    surfaceContainerHighest = KineticLightSurfaceContainerHighest,
    surfaceContainerLowest = KineticLightSurfaceContainerLowest,
    outline = KineticLightOutline,
    outlineVariant = KineticLightOutlineVariant,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    tertiary = Color(0xFF1B8A38),
    onTertiary = Color.White
)

// ═══════════════════════════════════════════════════════════════
// Safety Dark Theme — Mirror but using Cobalt
// ═══════════════════════════════════════════════════════════════
private val SafetyColorScheme = darkColorScheme(
    primary = KineticSecondary,
    onPrimary = Color.White,
    primaryContainer = KineticSecondaryContainer,
    onPrimaryContainer = Color.White,
    secondary = KineticPrimaryContainer,
    onSecondary = KineticOnPrimary,
    secondaryContainer = KineticPrimaryContainer,
    onSecondaryContainer = KineticOnPrimary,
    background = Color(0xFF031021),
    onBackground = KineticOnSurface,
    surface = Color(0xFF071931),
    onSurface = KineticOnSurface,
    surfaceVariant = Color(0xFF0F254B),
    onSurfaceVariant = Color(0xFFA5C5F1),
    surfaceContainer = Color(0xFF0F254B),
    surfaceContainerLow = Color(0xFF071931),
    surfaceContainerHigh = Color(0xFF14305D),
    surfaceContainerHighest = Color(0xFF1D3C6E),
    surfaceContainerLowest = Color(0xFF031021),
    outline = KineticOutline,
    outlineVariant = KineticOutlineVariant,
    error = KineticError,
    onError = Color.White,
    tertiary = KineticSuccess,
    onTertiary = Color.Black
)

// ═══════════════════════════════════════════════════════════════
// Safety Light Theme — Cobalt on white
// ═══════════════════════════════════════════════════════════════
private val SafetyLightColorScheme = lightColorScheme(
    primary = KineticLightSecondary,
    onPrimary = Color.White,
    primaryContainer = KineticLightSecondaryContainer,
    onPrimaryContainer = Color(0xFF001C3B),
    secondary = KineticLightPrimary,
    onSecondary = Color.White,
    secondaryContainer = KineticLightPrimaryContainer,
    onSecondaryContainer = KineticLightOnPrimaryContainer,
    background = Color(0xFFEFF4FB),
    onBackground = KineticLightOnSurface,
    surface = Color(0xFFF5F8FD),
    onSurface = KineticLightOnSurface,
    surfaceVariant = Color(0xFFDDE5F0),
    onSurfaceVariant = KineticLightOnSurfaceVariant,
    surfaceContainer = Color(0xFFE4EBF5),
    surfaceContainerLow = Color(0xFFF0F5FC),
    surfaceContainerHigh = Color(0xFFD8E2EE),
    surfaceContainerHighest = Color(0xFFCED9E6),
    surfaceContainerLowest = Color(0xFFF8FBFF),
    outline = KineticLightOutline,
    outlineVariant = KineticLightOutlineVariant,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    tertiary = Color(0xFF1B8A38),
    onTertiary = Color.White
)

/**
 * Dynamic theme that switches based on [Origin] and dark/light mode.
 * The color transition is animated over 300ms for a smooth visual effect.
 */
@Composable
fun PunteraDigitalTheme(
    origin: Origin = Origin.FOOT_SAFE,
    isDarkMode: Boolean = true,
    content: @Composable () -> Unit
) {
    val targetScheme = when {
        origin == Origin.FOOT_SAFE && isDarkMode  -> FootSafeColorScheme
        origin == Origin.FOOT_SAFE && !isDarkMode -> FootSafeLightColorScheme
        origin == Origin.SAFETY && isDarkMode     -> SafetyColorScheme
        else                                      -> SafetyLightColorScheme
    }

    // Animate each color property for smooth transition
    val animatedScheme = targetScheme.copy(
        primary = animateColorAsState(targetScheme.primary, tween(300), label = "primary").value,
        onPrimary = animateColorAsState(targetScheme.onPrimary, tween(300), label = "onPrimary").value,
        background = animateColorAsState(targetScheme.background, tween(300), label = "background").value,
        onBackground = animateColorAsState(targetScheme.onBackground, tween(300), label = "onBackground").value,
        surface = animateColorAsState(targetScheme.surface, tween(300), label = "surface").value,
        onSurface = animateColorAsState(targetScheme.onSurface, tween(300), label = "onSurface").value,
        surfaceVariant = animateColorAsState(targetScheme.surfaceVariant, tween(300), label = "surfaceVariant").value,
        onSurfaceVariant = animateColorAsState(targetScheme.onSurfaceVariant, tween(300), label = "onSurfaceVariant").value,
        primaryContainer = animateColorAsState(targetScheme.primaryContainer, tween(300), label = "primaryContainer").value,
        outline = animateColorAsState(targetScheme.outline, tween(300), label = "outline").value,
        error = animateColorAsState(targetScheme.error, tween(300), label = "error").value,
        onError = animateColorAsState(targetScheme.onError, tween(300), label = "onError").value,
        surfaceContainer = animateColorAsState(targetScheme.surfaceContainer, tween(300), label = "surfaceContainer").value,
        surfaceContainerHigh = animateColorAsState(targetScheme.surfaceContainerHigh, tween(300), label = "surfaceContainerHigh").value,
        surfaceContainerHighest = animateColorAsState(targetScheme.surfaceContainerHighest, tween(300), label = "surfaceContainerHighest").value,
        surfaceContainerLow = animateColorAsState(targetScheme.surfaceContainerLow, tween(300), label = "surfaceContainerLow").value,
        surfaceContainerLowest = animateColorAsState(targetScheme.surfaceContainerLowest, tween(300), label = "surfaceContainerLowest").value,
    )

    MaterialTheme(
        colorScheme = animatedScheme,
        typography = PunteraTypography,
        content = content
    )
}
