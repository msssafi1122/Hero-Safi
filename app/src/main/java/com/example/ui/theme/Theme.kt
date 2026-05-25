package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = NeonAqua,
    secondary = GlowLavender,
    tertiary = GlowPink,
    background = SpaceBlack,
    surface = DeepSteel,
    onBackground = Color(0xFFECEFF1),
    onSurface = Color(0xFFECEFF1),
    onPrimary = SpaceBlack,
    onSecondary = SpaceBlack,
    onTertiary = SpaceBlack,
    surfaceVariant = SteelBlue,
    onSurfaceVariant = Color(0xFFECEFF1)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = BrightTurquoise,
    secondary = LightSteel,
    tertiary = GlowLavender,
    background = Color(0xFFECEFF1),
    surface = Color.White,
    onBackground = SpaceBlack,
    onSurface = SpaceBlack,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force Dark Theme for a premium sci-fi system look
  dynamicColor: Boolean = false, // Disable dynamic colors to align 100% with chosen aesthetic
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
