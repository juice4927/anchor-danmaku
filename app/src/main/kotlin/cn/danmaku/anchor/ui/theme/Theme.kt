package cn.danmaku.anchor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AnchorColors = darkColorScheme(
    primary = Color(0xFF8B7CFF),
    onPrimary = Color(0xFF17102E),
    primaryContainer = Color(0xFF2B2158),
    onPrimaryContainer = Color(0xFFE5E0FF),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF1B1236),
    secondaryContainer = Color(0xFF33235E),
    onSecondaryContainer = Color(0xFFE9E0FF),
    tertiary = Color(0xFFFB7299),
    onTertiary = Color(0xFF3A0A1C),
    tertiaryContainer = Color(0xFF4A1630),
    onTertiaryContainer = Color(0xFFFFD9E4),
    background = Color(0xFF0B0D17),
    onBackground = Color(0xFFE8EBF5),
    surface = Color(0xFF121624),
    onSurface = Color(0xFFE8EBF5),
    surfaceVariant = Color(0xFF1C2134),
    onSurfaceVariant = Color(0xFFA8AFC4),
    outline = Color(0xFF2E3550),
    outlineVariant = Color(0xFF232A42),
    error = Color(0xFFFF6B81),
    onError = Color(0xFF3D0712),
)

private val AnchorShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AnchorTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AnchorColors,
        typography = AnchorTypography,
        shapes = AnchorShapes,
        content = content,
    )
}
