package cn.danmaku.anchor.ui.about

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import cn.danmaku.anchor.R
import cn.danmaku.anchor.ui.UiTags
import cn.danmaku.anchor.ui.theme.BiliPink
import cn.danmaku.anchor.ui.theme.BiliPinkLight

enum class AboutPage {
    About,
    Privacy,
}

@Composable
fun AboutScreen(
    page: AboutPage,
    onNavigateUp: () -> Unit,
) {
    val title = if (page == AboutPage.About) "关于" else "隐私说明"
    val body = if (page == AboutPage.About) {
        "主播弹幕台是非官方第三方工具，与哔哩哔哩无隶属或合作关系。应用仅在本机连接公开直播弹幕服务，不提供登录、发言或视频播放。B站接口变化可能导致功能暂时不可用。"
    } else {
        "应用只在本机保存设置、最近房间和用户黑名单，不上传到自建服务器。不使用 Cookie 或登录态，也不会读取存储、定位、通讯录、麦克风或相机。"
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateUp) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag(UiTags.AboutRoot),
        ) {
            Card(
                modifier = Modifier.testTag(UiTags.AboutBody),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(BiliPinkLight, BiliPink),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.foundation.Canvas(modifier = Modifier.size(20.dp)) {
                                val dark = Color(0xFF11151B)
                                val bubble = Path().apply {
                                    moveTo(size.width * 0.15f, size.height * 0.22f)
                                    lineTo(size.width * 0.85f, size.height * 0.22f)
                                    quadraticBezierTo(size.width * 0.92f, size.height * 0.22f, size.width * 0.92f, size.height * 0.32f)
                                    lineTo(size.width * 0.92f, size.height * 0.68f)
                                    quadraticBezierTo(size.width * 0.92f, size.height * 0.78f, size.width * 0.82f, size.height * 0.78f)
                                    lineTo(size.width * 0.58f, size.height * 0.78f)
                                    lineTo(size.width * 0.39f, size.height * 0.92f)
                                    lineTo(size.width * 0.44f, size.height * 0.78f)
                                    lineTo(size.width * 0.15f, size.height * 0.78f)
                                    quadraticBezierTo(size.width * 0.08f, size.height * 0.78f, size.width * 0.08f, size.height * 0.68f)
                                    lineTo(size.width * 0.08f, size.height * 0.32f)
                                    quadraticBezierTo(size.width * 0.08f, size.height * 0.22f, size.width * 0.15f, size.height * 0.22f)
                                    close()
                                }
                                drawPath(bubble, dark, style = Stroke(width = size.minDimension * 0.08f))
                                listOf(0.29f to 0.51f, 0.42f to 0.37f, 0.55f to 0.45f, 0.68f to 0.32f).forEach { (x, top) ->
                                    val width = size.width * 0.075f
                                    drawRoundRect(
                                        color = dark,
                                        topLeft = androidx.compose.ui.geometry.Offset(size.width * x, size.height * top),
                                        size = androidx.compose.ui.geometry.Size(width, size.height * (0.67f - top)),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2f),
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (page == AboutPage.About) "主播弹幕台" else "隐私保护",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
