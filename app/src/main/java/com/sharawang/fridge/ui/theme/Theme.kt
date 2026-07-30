package com.sharawang.fridge.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Status colours Material 3 has no slot for. Expiry state is the one thing on the
 * inventory screen that has to be readable at a glance, so it gets explicit colours
 * rather than being bent into `error` and `primary`.
 */
data class ExpiryColors(
    val expired: Color,
    val dueSoon: Color,
    val fresh: Color,
    val untracked: Color
)

private val LightExpiry = ExpiryColors(
    expired = Morandi.Rust,
    dueSoon = Morandi.Ochre,
    fresh = Morandi.Sage,
    untracked = Morandi.Stone
)

private val DarkExpiry = ExpiryColors(
    expired = Morandi.RustMuted,
    dueSoon = Morandi.OchreMuted,
    fresh = Morandi.SageMuted,
    untracked = Morandi.NightStone
)

val LocalExpiryColors = staticCompositionLocalOf { LightExpiry }

private val LightColors = lightColorScheme(
    primary = Morandi.Blue,
    onPrimary = Color.White,
    primaryContainer = Morandi.BlueLight,
    onPrimaryContainer = Morandi.BlueDeep,
    secondary = Morandi.BlueSoft,
    onSecondary = Morandi.BlueDeep,
    secondaryContainer = Morandi.BluePale,
    onSecondaryContainer = Morandi.BlueDeep,
    tertiary = Morandi.Sage,
    onTertiary = Color.White,
    tertiaryContainer = Morandi.SageLight,
    onTertiaryContainer = Morandi.SageDeep,
    background = Morandi.Canvas,
    onBackground = Morandi.Ink,
    surface = Morandi.Paper,
    onSurface = Morandi.Ink,
    surfaceVariant = Morandi.Linen,
    onSurfaceVariant = Morandi.InkSoft,
    surfaceContainer = Morandi.Canvas,
    surfaceContainerHigh = Morandi.Linen,
    outline = Morandi.Stone,
    outlineVariant = Morandi.Linen,
    error = Morandi.Rust,
    onError = Color.White,
    errorContainer = Morandi.RustLight,
    onErrorContainer = Morandi.RustDeep
)

private val DarkColors = darkColorScheme(
    primary = Morandi.BlueMuted,
    onPrimary = Morandi.BlueDeep,
    primaryContainer = Morandi.BlueShadow,
    onPrimaryContainer = Morandi.BlueLight,
    secondary = Morandi.BlueSoft,
    onSecondary = Morandi.BlueDeep,
    secondaryContainer = Morandi.BlueShadow,
    onSecondaryContainer = Morandi.BlueLight,
    tertiary = Morandi.SageMuted,
    onTertiary = Morandi.SageDeep,
    tertiaryContainer = Color(0xFF3D473B),
    onTertiaryContainer = Morandi.SageLight,
    background = Morandi.NightCanvas,
    onBackground = Morandi.NightInk,
    surface = Morandi.NightPaper,
    onSurface = Morandi.NightInk,
    surfaceVariant = Morandi.NightLinen,
    onSurfaceVariant = Morandi.NightInkSoft,
    surfaceContainer = Morandi.NightCanvas,
    surfaceContainerHigh = Morandi.NightLinen,
    outline = Morandi.NightStone,
    outlineVariant = Morandi.NightLinen,
    error = Morandi.RustMuted,
    onError = Morandi.RustDeep,
    errorContainer = Color(0xFF503121),
    onErrorContainer = Morandi.RustLight
)

/**
 * Material You dynamic colour is deliberately not used: it would pull the whole app toward
 * whatever the user's wallpaper is and wash out the Morandi palette. Flip [dynamicColor] on
 * only if that trade stops mattering.
 */
@Composable
fun FridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(
        LocalExpiryColors provides if (darkTheme) DarkExpiry else LightExpiry
    ) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
