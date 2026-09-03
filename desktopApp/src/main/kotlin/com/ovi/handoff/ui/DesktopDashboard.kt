package com.ovi.handoff.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.handoff.core.DesktopConfigManager
import com.ovi.handoff.core.RelayClient
import com.ovi.handoff.shared.model.*
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

@Composable
fun QrCodeView(content: String, modifier: Modifier = Modifier, fgColor: Color = Color.White, bgColor: Color = Color.Transparent) {
    val bitMatrix = remember(content) {
        val writer = QRCodeWriter()
        writer.encode(content, BarcodeFormat.QR_CODE, 200, 200)
    }

    Canvas(modifier = modifier) {
        drawRect(color = bgColor, size = size)
        val cellWidth = size.width / bitMatrix.width
        val cellHeight = size.height / bitMatrix.height

        for (x in 0 until bitMatrix.width) {
            for (y in 0 until bitMatrix.height) {
                if (bitMatrix.get(x, y)) {
                    drawRect(
                        color = fgColor,
                        topLeft = Offset(x * cellWidth, y * cellHeight),
                        size = Size(cellWidth, cellHeight)
                    )
                }
            }
        }
    }
}

data class ActivityLogItem(
    val id: String,
    val time: String,
    val agent: String,
    val summary: String,
    val status: String,
    val isSuccess: Boolean
)

@Composable
fun DesktopDashboard() {
    val coroutineScope = rememberCoroutineScope()
    var pairId by remember { mutableStateOf(DesktopConfigManager.getPairId()) }
    val relayHost by remember { mutableStateOf(DesktopConfigManager.getRelayHost()) }
    var isDispatching by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Desktop bridge is ready and listening.") }
    val activityLogs = remember { mutableStateListOf<ActivityLogItem>() }

    fun copyToClipboard(text: String) {
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        statusMessage = "Copied to clipboard: $text"
    }

    fun dispatchRequest(
        title: String,
        agent: String,
        requestBuilder: () -> PermissionRequest
    ) {
        coroutineScope.launch {
            isDispatching = true
            statusMessage = "Dispatching $title to mobile device..."
            try {
                val client = RelayClient(relayHost)
                val request = requestBuilder()
                val decision = client.sendRequestAndWaitForDecision(pairId, request)
                if (decision != null) {
                    val approved = decision.decision in listOf("approve", "approve_once", "proceed_plan", "answer_question")
                    val feedbackNote = if (!decision.feedback.isNullOrBlank()) " (${decision.feedback})" else ""
                    val selected = decision.selectedOptions
                    val selectedNote = if (!selected.isNullOrEmpty()) " -> ${selected.first()}" else ""
                    val resultStatus = "${decision.decision}$selectedNote$feedbackNote"

                    activityLogs.add(
                        0,
                        ActivityLogItem(
                            id = UUID.randomUUID().toString(),
                            time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                            agent = agent,
                            summary = title,
                            status = resultStatus,
                            isSuccess = approved
                        )
                    )
                    statusMessage = "Decision received: $resultStatus"
                } else {
                    activityLogs.add(
                        0,
                        ActivityLogItem(
                            id = UUID.randomUUID().toString(),
                            time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                            agent = agent,
                            summary = title,
                            status = "Timed out / Cancelled",
                            isSuccess = false
                        )
                    )
                    statusMessage = "Request timed out or cancelled by phone."
                }
            } catch (e: Exception) {
                statusMessage = "Error: ${e.message}"
            } finally {
                isDispatching = false
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF80D4FF),
            onPrimary = Color(0xFF003549),
            primaryContainer = Color(0xFF004D68),
            onPrimaryContainer = Color(0xFFC3E8FF),
            surface = Color(0xFF131B24),
            onSurface = Color(0xFFE2E8F0),
            surfaceVariant = Color(0xFF1E293B),
            onSurfaceVariant = Color(0xFF94A3B8),
            background = Color(0xFF0B1118)
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Handoff",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "Desktop Control Center v1.1.0",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Zero-Trust Remote Approval Bridge & MCP Server for Coding Agents",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Relay Status Badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF064E3B).copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Relay Active: $relayHost",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF6EE7B7)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Status Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isDispatching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            text = statusMessage,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Main 2-Column Content
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Left Column: Pairing & Testing
                    Column(
                        modifier = Modifier.weight(1.1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Pairing Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "Mobile Pairing Session",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Enter this Pair ID in the Handoff Android app to establish the encrypted bridge.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val url = "handoff://pair?pairId=$pairId&host=$relayHost"
                                    
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White,
                                        modifier = Modifier.padding(end = 16.dp)
                                    ) {
                                        QrCodeView(
                                            content = url,
                                            modifier = Modifier.size(110.dp).padding(8.dp),
                                            fgColor = Color.Black,
                                            bgColor = Color.White
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF0F172A),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = pairId,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.height(12.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                FilledTonalButton(
                                                    onClick = { copyToClipboard(pairId) },
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Copy Code", fontSize = 12.sp)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        pairId = DesktopConfigManager.generateNewPairId()
                                                        statusMessage = "Generated new Pair ID: $pairId"
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Regenerate", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Simulation / Test Dispatchers Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "Simulate AI Agent Prompts",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Dispatch test permission requests to your connected Android phone:",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            dispatchRequest("Terminal Command", "Cursor") {
                                                PermissionRequest(
                                                    id = UUID.randomUUID().toString(),
                                                    protocolVersion = "1.0",
                                                    agent = AgentInfo(id = "cursor", name = "Cursor", version = "0.45.2"),
                                                    session = SessionInfo(id = pairId, project = "HandOff", workspace = "handoff"),
                                                    permission = PermissionInfo(
                                                        type = "terminal",
                                                        command = "npx prisma migrate dev --name add_rbac_tables",
                                                        description = "Execute production database schema migration",
                                                        cwd = "c:\\Users\\USERAS\\Desktop\\HandOff\\handoff"
                                                    ),
                                                    risk = RiskInfo(level = "critical", reasons = listOf("Modifies database schema", "Requires table locks")),
                                                    options = listOf("approve", "deny"),
                                                    createdAt = Instant.now().toString(),
                                                    expiresAt = Instant.now().plusSeconds(300).toString()
                                                )
                                            }
                                        },
                                        enabled = !isDispatching,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("🐚 Shell Command", fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = {
                                            dispatchRequest("Plan Review", "Antigravity") {
                                                PermissionRequest(
                                                    id = UUID.randomUUID().toString(),
                                                    protocolVersion = "1.0",
                                                    agent = AgentInfo(id = "antigravity", name = "Antigravity", version = "2.2.0"),
                                                    session = SessionInfo(id = pairId, project = "HandOff", workspace = "handoff"),
                                                    permission = PermissionInfo(type = "plan", description = "Architecture plan review"),
                                                    risk = RiskInfo(level = "high", reasons = listOf("Full-stack authentication overhaul")),
                                                    options = listOf("proceed_plan", "deny"),
                                                    createdAt = Instant.now().toString(),
                                                    expiresAt = Instant.now().plusSeconds(300).toString(),
                                                    plan = PlanPayload(
                                                        title = "Full-Stack Auth & RBAC Security Overhaul",
                                                        summary = "Scaffolds Argon2 password hashing, short-lived JWTs in HttpOnly cookies, and role-based route guards across mobile and web.",
                                                        userReviewRequired = listOf(
                                                            "Requires Redis server instance for revoked token blacklist",
                                                            "Existing user sessions will be invalidated on release"
                                                        )
                                                    )
                                                )
                                            }
                                        },
                                        enabled = !isDispatching,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("📋 Plan Review", fontSize = 12.sp)
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            dispatchRequest("Architecture Question", "Antigravity") {
                                                PermissionRequest(
                                                    id = UUID.randomUUID().toString(),
                                                    protocolVersion = "1.0",
                                                    agent = AgentInfo(id = "antigravity", name = "Antigravity", version = "2.2.0"),
                                                    session = SessionInfo(id = pairId, project = "HandOff", workspace = "handoff"),
                                                    permission = PermissionInfo(type = "question", description = "Database decision"),
                                                    risk = RiskInfo(level = "medium", reasons = listOf("Architectural dependency decision")),
                                                    options = listOf("answer_question", "cancel"),
                                                    createdAt = Instant.now().toString(),
                                                    expiresAt = Instant.now().plusSeconds(300).toString(),
                                                    question = QuestionPayload(
                                                        question = "Which database architecture should we use for production?",
                                                        options = listOf(
                                                            "PostgreSQL + Prisma ORM (Recommended)",
                                                            "Cloud Firestore with Zero-Trust Rules",
                                                            "SQLite with Room KMP Multiplatform"
                                                        ),
                                                        isMultiSelect = false
                                                    )
                                                )
                                            }
                                        },
                                        enabled = !isDispatching,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("❓ User Question", fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = {
                                            dispatchRequest("Code Patch Diff", "Codex") {
                                                PermissionRequest(
                                                    id = UUID.randomUUID().toString(),
                                                    protocolVersion = "1.0",
                                                    agent = AgentInfo(id = "codex", name = "Codex", version = "2026.1"),
                                                    session = SessionInfo(id = pairId, project = "HandOff", workspace = "handoff"),
                                                    permission = PermissionInfo(
                                                        type = "patch",
                                                        description = "Apply authentication security patch to AuthRepository.kt",
                                                        target = "src/main/kotlin/auth/AuthRepository.kt",
                                                        diff = "--- a/src/main/kotlin/auth/AuthRepository.kt\n+++ b/src/main/kotlin/auth/AuthRepository.kt\n@@ -14,4 +14,6 @@\n-    return legacyVerify(token)\n+    if (token.isBlank()) return false\n+    return argon2Verify(token)"
                                                    ),
                                                    risk = RiskInfo(level = "high", reasons = listOf("Cryptographic password verification patch")),
                                                    options = listOf("approve", "deny"),
                                                    createdAt = Instant.now().toString(),
                                                    expiresAt = Instant.now().plusSeconds(300).toString()
                                                )
                                            }
                                        },
                                        enabled = !isDispatching,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("📝 Code Patch", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Right Column: Live Stream & MCP Integration Snippet
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Live Activity Log
                        Card(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Live Activity & Decisions",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (activityLogs.isNotEmpty()) {
                                        TextButton(onClick = { activityLogs.clear() }) {
                                            Text("Clear", fontSize = 12.sp)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                if (activityLogs.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No requests dispatched yet.\nUse the simulator buttons or connect an AI agent via MCP.",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(activityLogs) { log ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = log.time,
                                                                fontSize = 11.sp,
                                                                fontFamily = FontFamily.Monospace,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Spacer(Modifier.width(8.dp))
                                                            Text(
                                                                text = log.agent,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                        Spacer(Modifier.height(2.dp))
                                                        Text(
                                                            text = log.summary,
                                                            fontSize = 13.sp,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }

                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = if (log.isSuccess) Color(0xFF064E3B) else Color(0xFF7F1D1D)
                                                    ) {
                                                        Text(
                                                            text = log.status,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                            color = if (log.isSuccess) Color(0xFF6EE7B7) else Color(0xFFFCA5A5)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // MCP Server Stdio Snippet Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "AI Agent MCP Configuration",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Add to your Claude Code, Cursor, or Antigravity config:",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0F172A),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = """{ "command": "./gradlew", "args": [":desktopApp:run", "--args=--mcp"] }""",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFFA5B4FC),
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
