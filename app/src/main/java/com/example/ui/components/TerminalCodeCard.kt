package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanPrimary

@Composable
fun TerminalCodeCard(
    adbCommand: String,
    pipeWireCommand: String,
    pulseAudioCommand: String,
    testCommand: String,
    fullScript: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        "PipeWire Nativo (Ubuntu 24.04)",
        "Script Auto-Reconexão (.sh)",
        "PulseAudio Fallback",
        "Teste Rápido",
        "Comando ADB"
    )
    val currentCode = when (selectedTab) {
        0 -> pipeWireCommand
        1 -> fullScript
        2 -> pulseAudioCommand
        3 -> testCommand
        else -> adbCommand
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF090E16))
            .border(1.dp, Color(0xFF1E2D42), RoundedCornerShape(16.dp))
    ) {
        // Window Title Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F1722))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Window control dots
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF5F56)))
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFFBD2E)))
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF27C93F)))

                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Terminal Ubuntu (Bash)",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF94A3B8),
                    fontFamily = FontFamily.Monospace
                )
            }

            ElevatedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("DroidMic Command", currentCode)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Comando copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = CyanPrimary,
                    contentColor = Color(0xFF00222D)
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(30.dp)
                    .testTag("copy_command_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copiar comando",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copiar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF0B121C),
            contentColor = CyanPrimary,
            edgePadding = 8.dp,
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == index) CyanPrimary else Color(0xFF64748B)
                        )
                    }
                )
            }
        }

        // Terminal Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF05080E))
                .padding(12.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = currentCode,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Color(0xFF7DD3FC),
                lineHeight = 18.sp
            )
        }
    }
}
