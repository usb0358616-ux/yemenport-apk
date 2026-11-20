package com.example.yemenport.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yemenport.R

val CairoFontFamily = FontFamily(
    Font(R.font.cairo, FontWeight.Normal),
    Font(R.font.cairo, FontWeight.Medium),
    Font(R.font.cairo, FontWeight.SemiBold),
    Font(R.font.cairo, FontWeight.Bold)
)

val SovereignNavy = Color(0xFF1E3A5F)
val SovereignNavyDark = Color(0xFF0F2137)
val SovereignGold = Color(0xFFD97706)
val SovereignGoldLight = Color(0xFFF59E0B)
val SovereignEmerald = Color(0xFF059669)
val SovereignCrimson = Color(0xFFDC2626)
val SovereignCrimsonBg = Color(0xFFFEE2E2)
val SovereignSurfaceLight = Color(0xFFF5F7FA)
val SovereignCardLight = Color(0xFFFFFFFF)
val SovereignSurfaceDark = Color(0xFF0B1320)
val SovereignCardDark = Color(0xFF152236)

private val LightColors = lightColorScheme(
    primary = SovereignNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4F7),
    onPrimaryContainer = Color(0xFF0C1E36),
    secondary = SovereignGold,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF451A03),
    tertiary = SovereignEmerald,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF064E3B),
    error = SovereignCrimson,
    onError = Color.White,
    errorContainer = SovereignCrimsonBg,
    onErrorContainer = Color(0xFF7F1D1D),
    background = SovereignSurfaceLight,
    onBackground = Color(0xFF0F172A),
    surface = SovereignCardLight,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFF94A3B8)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF93C5FD),
    onPrimary = Color(0xFF081B33),
    primaryContainer = SovereignNavy,
    onPrimaryContainer = Color(0xFFE2EEFC),
    secondary = SovereignGoldLight,
    onSecondary = Color(0xFF2E1500),
    secondaryContainer = Color(0xFF78350F),
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF022C22),
    tertiaryContainer = Color(0xFF065F46),
    onTertiaryContainer = Color(0xFFD1FAE5),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    background = SovereignSurfaceDark,
    onBackground = Color(0xFFF1F5F9),
    surface = SovereignCardDark,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF22334D),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF64748B)
)

private val SovereignTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = CairoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = CairoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = CairoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = CairoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = CairoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = CairoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = CairoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)

private val SovereignShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun YemenPortTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = SovereignTypography,
            shapes = SovereignShapes,
            content = content
        )
    }
}
