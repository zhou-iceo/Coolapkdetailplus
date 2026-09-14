package com.atxz.coolapkdetailplus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.highcapable.yukihookapi.YukiHookAPI

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MiuixPluginAppScreen()
        }
    }
}

@Composable
fun MiuixPluginAppScreen() {
    val context = LocalContext.current
    var logBuffer by remember { mutableStateOf(LogManager.getLogs(context)) }
    val isActive = YukiHookAPI.Status.isModuleActive

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val line = intent?.getStringExtra(LogManager.EXTRA_LOG_TEXT)
                if (!line.isNullOrBlank()) {
                    logBuffer = if (logBuffer.isBlank() || logBuffer.startsWith("等待")) {
                        line
                    } else {
                        logBuffer + "\n" + line
                    }
                }
            }
        }
        val filter = IntentFilter(LogManager.ACTION_NEW_LOG)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Throwable) {
                // Ignore
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF2F2F7)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "CoolapkDetailPlus",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1C1E),
                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
            )

            MiuixGroupCard {
                MiuixInfoRow(
                    title = "模块版本",
                    value = "v0.0.5"
                )

                MiuixDivider()

                MiuixInfoRow(
                    title = "激活状态",
                    valueWidget = {
                        MiuixStatusBadge(isActive = isActive)
                    }
                )

                MiuixDivider()

                MiuixInfoRow(
                    title = "API 版本",
                    value = "LibXposed API 102 / Yuki 1.3.2"
                )

                MiuixDivider()

                MiuixInfoRow(
                    title = "仓库地址",
                    value = "git@github.com:zhou-iceo/Coolapkdetailplus.git"
                )
            }

            MiuixLogConsoleCard(
                logText = if (logBuffer.isBlank()) "等待 Hook 事件触发...\n" else logBuffer,
                onClearLog = {
                    LogManager.clearLogs(context)
                    logBuffer = "--> 日志已清空 <--\n"
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun MiuixGroupCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            content()
        }
    }
}

@Composable
fun MiuixInfoRow(
    title: String,
    value: String? = null,
    valueWidget: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF1C1C1E)
        )

        if (valueWidget != null) {
            valueWidget()
        } else if (value != null) {
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF8E8E93)
            )
        }
    }
}

@Composable
fun MiuixStatusBadge(isActive: Boolean) {
    val bgColor = if (isActive) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
    val dotColor = if (isActive) Color(0xFF33A261) else Color(0xFFE53935)
    val textColor = if (isActive) Color(0xFF2E7D32) else Color(0xFFC62828)
    val textStr = if (isActive) "已激活 (Active)" else "未激活 (Inactive)"

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = textStr,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }
    }
}

@Composable
fun MiuixDivider() {
    HorizontalDivider(
        color = Color(0xFFF2F2F7),
        thickness = 1.dp
    )
}

@Composable
fun MiuixLogConsoleCard(
    logText: String,
    onClearLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(logText) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "插件运行日志",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1C1E)
                )

                TextButton(onClick = onClearLog) {
                    Text(
                        text = "清空日志",
                        fontSize = 13.sp,
                        color = Color(0xFFFF3B30)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1C1C1E), RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = logText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF34C759),
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                )
            }
        }
    }
}
