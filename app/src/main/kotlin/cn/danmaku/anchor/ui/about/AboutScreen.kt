package cn.danmaku.anchor.ui.about

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cn.danmaku.anchor.ui.UiTags

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
            IconButton(onClick = onNavigateUp) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
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
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.testTag(UiTags.AboutBody),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(
                    body,
                    modifier = Modifier.padding(18.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
