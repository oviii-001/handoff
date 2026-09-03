package com.ovi.handoff.mobile.core.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.handoff.mobile.core.theme.*

@Composable
public fun TerminalCard(
    command: String,
    toolType: String = "run_command",
    cwd: String? = null,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2000)
            copied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TerminalBg)
            .border(1.dp, TerminalBorder, RoundedCornerShape(12.dp))
    ) {
        // Terminal Window Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Three macOS dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(TerminalDotRed))
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(TerminalDotYellow))
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(TerminalDotGreen))

                Spacer(modifier = Modifier.width(6.dp))

                // Tool chip
                Text(
                    text = toolType,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = MonospaceFont,
                    fontWeight = FontWeight.Medium
                )
            }

            // Copy Action
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkBorder)
                    .clickable {
                        clipboardManager.setText(AnnotatedString(command))
                        copied = true
                        Toast.makeText(context, "Command copied", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (copied) "✓ Copied" else "Copy",
                    color = if (copied) TerminalGreen else TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = MonospaceFont
                )
            }
        }

        // CWD Badge (if present)
        if (!cwd.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📂 $cwd",
                    color = TerminalCyan,
                    fontSize = 11.sp,
                    fontFamily = MonospaceFont
                )
            }
        }

        // Command Content
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = "$ ",
                color = TerminalGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MonospaceFont
            )
            Text(
                text = command,
                color = TerminalText,
                fontSize = 13.sp,
                fontFamily = MonospaceFont,
                lineHeight = 18.sp
            )
        }
    }
}
