package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = SnapEmerald,
    onPrimary = SnapObsidian,
    primaryContainer = SnapEmeraldContainer,
    onPrimaryContainer = SnapEmerald,
    secondary = SnapCoral,
    onSecondary = Color.White,
    secondaryContainer = SnapCoralContainer,
    onSecondaryContainer = Color(0xFFFFB3C6),
    tertiary = SnapElectricBlue,
    onTertiary = SnapObsidian,
    background = SnapObsidian,
    onBackground = SnapTextPrimaryDark,
    surface = SnapSurfaceDark,
    onSurface = SnapTextPrimaryDark,
    surfaceVariant = SnapSurfaceElevated,
    onSurfaceVariant = SnapTextSecondaryDark,
    outline = SnapOutlineDark,
    error = SnapCoral,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = SnapEmeraldDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0FBE9),
    onPrimaryContainer = Color(0xFF003824),
    secondary = SnapCoral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCE5),
    onSecondaryContainer = Color(0xFF4A0016),
    tertiary = SnapElectricBlue,
    onTertiary = Color.White,
    background = SnapLightBg,
    onBackground = SnapTextPrimaryLight,
    surface = SnapLightSurface,
    onSurface = SnapTextPrimaryLight,
    surfaceVariant = SnapLightSurfaceVariant,
    onSurfaceVariant = SnapTextSecondaryLight,
    outline = SnapOutlineLight,
    error = SnapCoral,
    onError = Color.White
)

val SnapShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun SnapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SnapTypography,
        shapes = SnapShapes,
        content = content
    )
}
