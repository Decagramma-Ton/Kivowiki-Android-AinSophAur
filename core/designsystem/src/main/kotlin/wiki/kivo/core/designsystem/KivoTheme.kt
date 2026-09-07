package wiki.kivo.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import wiki.kivo.core.model.AppSettings
import wiki.kivo.core.model.ThemeMode

private val DayColors =
    lightColorScheme(
        primary = Color(0xFF087DA6),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDDF3FA),
        onPrimaryContainer = Color(0xFF075671),
        background = Color(0xFFF5F8FA),
        onBackground = Color(0xFF183044),
        surface = Color.White,
        onSurface = Color(0xFF183044),
        surfaceVariant = Color(0xFFEDF3F7),
        onSurfaceVariant = Color(0xFF52687A),
        outline = Color(0xFF738795),
        outlineVariant = Color(0xFFDCE6EC),
        secondary = Color(0xFF587084),
        secondaryContainer = Color(0xFFE8F0F5),
        onSecondaryContainer = Color(0xFF26465D),
        tertiary = Color(0xFFA1662A),
        error = Color(0xFFB3261E),
    )
private val NightColors =
    darkColorScheme(
        primary = Color(0xFF79D5EF),
        onPrimary = Color(0xFF003544),
        primaryContainer = Color(0xFF173E50),
        onPrimaryContainer = Color(0xFFB5E8F7),
        background = Color(0xFF0E1A24),
        onBackground = Color(0xFFE4EDF5),
        surface = Color(0xFF162631),
        onSurface = Color(0xFFE4EDF5),
        surfaceVariant = Color(0xFF213746),
        onSurfaceVariant = Color(0xFFB0C3D1),
        outline = Color(0xFF8BA3B3),
        outlineVariant = Color(0xFF304957),
        secondary = Color(0xFFB0C3D1),
        secondaryContainer = Color(0xFF263E4E),
        onSecondaryContainer = Color(0xFFD2E7F4),
        tertiary = Color(0xFFE3BA86),
        error = Color(0xFFFFB4AB),
    )
val LocalKivoSettings = staticCompositionLocalOf { AppSettings() }
val LocalKivoDark = staticCompositionLocalOf { false }

/** 所有 feature 共用语义色与中文系统字体，首帧不依赖网络字体。 */
@Composable
fun KivoTheme(settings: AppSettings = AppSettings(), content: @Composable () -> Unit) {
    val dark =
        when (settings.theme) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }
    val typography = remember {
        val base = FontFamily.SansSerif
        Typography(
            headlineLarge =
                TextStyle(
                    fontFamily = base,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    lineHeight = 40.sp,
                ),
            headlineMedium =
                TextStyle(
                    fontFamily = base,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    lineHeight = 36.sp,
                ),
            titleLarge =
                TextStyle(
                    fontFamily = base,
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp,
                    lineHeight = 30.sp,
                ),
            titleMedium =
                TextStyle(
                    fontFamily = base,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                ),
            titleSmall =
                TextStyle(
                    fontFamily = base,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                ),
            bodyLarge =
                TextStyle(
                    fontFamily = base,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    lineHeight = 26.sp,
                ),
            bodyMedium =
                TextStyle(
                    fontFamily = base,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 23.sp,
                ),
            bodySmall =
                TextStyle(
                    fontFamily = base,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                ),
            labelLarge =
                TextStyle(
                    fontFamily = base,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
            labelMedium =
                TextStyle(
                    fontFamily = base,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                ),
            labelSmall =
                TextStyle(
                    fontFamily = base,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                ),
        )
    }
    CompositionLocalProvider(LocalKivoSettings provides settings, LocalKivoDark provides dark) {
        MaterialTheme(
            colorScheme = if (dark) NightColors else DayColors,
            typography = typography,
            content = content,
        )
    }
}
