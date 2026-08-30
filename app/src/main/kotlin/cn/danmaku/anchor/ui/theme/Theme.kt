package cn.danmaku.anchor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Bilibili 深色主题配色。
 * 主色取自 B 站品牌粉 #FB7299，辅助色取经典链接蓝 #00A1D6，
 * 背景/卡片/文本沿用 B 站深色模式的 #18191C / #232428 / #F2F3F5 体系。
 */
/** B 站品牌粉。 */
val BiliPink = Color(0xFFFB7299)

/** B 站品牌浅粉，用于渐变起点。 */
val BiliPinkLight = Color(0xFFFF9BB2)

/** B 站经典链接蓝。 */
val BiliBlue = Color(0xFF00A1D6)

/** SC 醒目留言金色。 */
val BiliGold = Color(0xFFFFC857)

/** 礼物金色。 */
val BiliGiftGold = Color(0xFFFFD24D)

private val BiliDarkColors = darkColorScheme(
    primary = Color(0xFFFB7299),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF3A1D29),
    onPrimaryContainer = Color(0xFFFFD9E4),
    secondary = Color(0xFF00A1D6),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF102C36),
    onSecondaryContainer = Color(0xFFBFE9F7),
    tertiary = Color(0xFFFF9BB2),
    onTertiary = Color(0xFF3A0A1C),
    tertiaryContainer = Color(0xFF3A2029),
    onTertiaryContainer = Color(0xFFFFD9E4),
    background = Color(0xFF0F1216),
    onBackground = Color(0xFFF2F3F5),
    surface = Color(0xFF171B21),
    onSurface = Color(0xFFF2F3F5),
    surfaceVariant = Color(0xFF1D2229),
    onSurfaceVariant = Color(0xFFC9CCD0),
    outline = Color(0xFF3A424C),
    outlineVariant = Color(0xFF293039),
    error = Color(0xFFFF6B81),
    onError = Color(0xFF3D0712),
)

private val BiliShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

@Composable
fun AnchorTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = BiliDarkColors,
        typography = AnchorTypography,
        shapes = BiliShapes,
        content = content,
    )
}
