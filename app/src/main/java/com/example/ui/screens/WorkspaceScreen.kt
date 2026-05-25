package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Note
import com.example.data.VaultImage
import com.example.ui.theme.NeonAqua
import com.example.ui.theme.DeepSteel
import com.example.ui.theme.SteelBlue
import com.example.ui.theme.LightSteel
import com.example.ui.theme.GlowLavender
import com.example.ui.theme.GlowPink
import com.example.ui.theme.SpaceBlack
import com.example.ui.theme.PairColor
import com.example.ui.viewmodel.WorkspaceViewModel
import kotlin.math.roundToInt
import java.io.File
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.tween

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val systemOverlayActive by viewModel.isOverlayActive.collectAsState()

    // Screen State for the 5 Virtual Draggable Windows
    var aiOpen by remember { mutableStateOf(false) }
    var calcOpen by remember { mutableStateOf(false) }
    var notesOpen by remember { mutableStateOf(false) }
    var screenshotOpen by remember { mutableStateOf(false) }
    var vaultOpen by remember { mutableStateOf(false) }

    // Floating Window Initial Positions (staggered slightly)
    var aiOffset by remember { mutableStateOf(Offset(25f, 50f)) }
    var calcOffset by remember { mutableStateOf(Offset(50f, 150f)) }
    var notesOffset by remember { mutableStateOf(Offset(75f, 250f)) }
    var screenshotOffset by remember { mutableStateOf(Offset(100f, 350f)) }
    var vaultOffset by remember { mutableStateOf(Offset(125f, 100f)) }

    // Settings panel visibility
    var showSettings by remember { mutableStateOf(false) }

    // Simulated Canvas screenshot cache for the flash effect & preview
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showScreenFlash by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SpaceBlack, DeepSteel, SpaceBlack)
                )
            )
    ) {
        // --- Grid Background Decoration (Atmospheric matrix dots) ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            val columns = (size.width / 40.dp.toPx()).toInt()
            val rows = (size.height / 40.dp.toPx()).toInt()
            for (i in 0 until columns) {
                for (j in 0 until rows) {
                    drawCircle(
                        color = Color(0x1A00E5FF),
                        radius = 1.2f.dp.toPx(),
                        center = Offset((i * 40.dp.toPx()) + 20.dp.toPx(), (j * 40.dp.toPx()) + 20.dp.toPx())
                    )
                }
            }
        }

        // --- Main Top Navigation Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .statusBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "FLOATING DECK",
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    color = NeonAqua
                )
                Text(
                    text = "Interactive Virtual Systems Workspace",
                    fontSize = 12.sp,
                    color = PairColor.TextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = { showSettings = !showSettings },
                modifier = Modifier
                    .background(SteelBlue, shape = CircleShape)
                    .border(1.dp, Color(0x3300E5FF), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = NeonAqua
                )
            }
        }

        // --- Welcome Tip Overlay (When no window is open) ---
        if (!aiOpen && !calcOpen && !notesOpen && !screenshotOpen && !vaultOpen && !showSettings) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xCC161A26)),
                    border = BorderStroke(1.dp, Color(0x2200E5FF)),
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color(0x1E00E5FF), shape = CircleShape)
                                .padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Floating Hub",
                                tint = NeonAqua,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Launch Floating Tools",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Use the bottom floating dock to launch separate floating application windows. You can drag them around, resize them, and minimize/maximize them just like a desktop workspace!",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = PairColor.TextSecondary,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // Overlay Service Controller Quick Action
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0x330C0F17)),
                            border = BorderStroke(1.dp, Color(0x1F00E5FF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "System Overlay Service",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (systemOverlayActive) "Service is active over other apps" else "Enable system-wide floating widget",
                                        fontSize = 11.sp,
                                        color = PairColor.TextSecondary
                                    )
                                }
                                Switch(
                                    checked = systemOverlayActive,
                                    onCheckedChange = { viewModel.toggleOverlayService() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = NeonAqua,
                                        checkedTrackColor = Color(0xFF005266),
                                        uncheckedThumbColor = Color.Gray,
                                        uncheckedTrackColor = SteelBlue
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Simulated Virtual Windows Viewport Container ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 100.dp, bottom = 120.dp)
        ) {
            // 1. Virtual Floating AI Window
            if (aiOpen) {
                FloatingWindow(
                    title = "AI Chat (Groq)",
                    icon = Icons.Default.Face,
                    glowColor = NeonAqua,
                    offset = aiOffset,
                    onOffsetChange = { aiOffset = it },
                    onClose = { aiOpen = false },
                    initialWidth = 330.dp,
                    initialHeight = 440.dp
                ) {
                    AiChatWidgetContent(viewModel)
                }
            }

            // 2. Virtual Floating Calculator Window
            if (calcOpen) {
                FloatingWindow(
                    title = "Calculator",
                    icon = Icons.Default.Build,
                    glowColor = GlowPink,
                    offset = calcOffset,
                    onOffsetChange = { calcOffset = it },
                    onClose = { calcOpen = false },
                    initialWidth = 280.dp,
                    initialHeight = 400.dp
                ) {
                    CalculatorWidgetContent()
                }
            }

            // 3. Virtual Floating Notes Window
            if (notesOpen) {
                FloatingWindow(
                    title = "Notes",
                    icon = Icons.Default.Create,
                    glowColor = GlowLavender,
                    offset = notesOffset,
                    onOffsetChange = { notesOffset = it },
                    onClose = { notesOpen = false },
                    initialWidth = 310.dp,
                    initialHeight = 420.dp
                ) {
                    NotesWidgetContent(viewModel)
                }
            }

            // 4. Virtual Floating Screenshot Help Window
            if (screenshotOpen) {
                FloatingWindow(
                    title = "Screenshot Hub",
                    icon = Icons.Default.Info,
                    glowColor = Color.LightGray,
                    offset = screenshotOffset,
                    onOffsetChange = { screenshotOffset = it },
                    onClose = { screenshotOpen = false },
                    initialWidth = 300.dp,
                    initialHeight = 360.dp
                ) {
                    ScreenshotWidgetContent(
                        capturedBitmap = capturedBitmap,
                        onCaptureSimulated = {
                            showScreenFlash = true
                            Toast.makeText(context, "Workspace Screenshot Captured (Simulated)", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // 5. Virtual Floating Vault Window (Passcode Protected)
            if (vaultOpen) {
                FloatingWindow(
                    title = "Secure Vault",
                    icon = Icons.Default.Lock,
                    glowColor = Color(0xFFFFD740),
                    offset = vaultOffset,
                    onOffsetChange = { vaultOffset = it },
                    onClose = { vaultOpen = false },
                    initialWidth = 340.dp,
                    initialHeight = 460.dp
                ) {
                    VaultWidgetContent(viewModel)
                }
            }
        }

        // --- Settings Dialog Bottomsheet or Modal ---
        if (showSettings) {
            SettingsOverlay(
                viewModel = viewModel,
                onDismiss = { showSettings = false }
            )
        }

        // --- Bottom Workspace Launcher Dock ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .align(Alignment.BottomCenter)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xEE161A26)),
                border = BorderStroke(1.dp, Color(0x3300E5FF)),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 380.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // AI Launch Icon
                    DockIcon(
                        icon = Icons.Default.Face,
                        label = "AI",
                        isActive = aiOpen,
                        tintColor = NeonAqua,
                        onClick = { aiOpen = !aiOpen }
                    )

                    // Calculator Launch Icon
                    DockIcon(
                        icon = Icons.Default.Build,
                        label = "Calc",
                        isActive = calcOpen,
                        tintColor = GlowPink,
                        onClick = { calcOpen = !calcOpen }
                    )

                    // Notes Launch Icon
                    DockIcon(
                        icon = Icons.Default.Create,
                        label = "Notes",
                        isActive = notesOpen,
                        tintColor = GlowLavender,
                        onClick = { notesOpen = !notesOpen }
                    )

                    // Screenshot Launch Icon
                    DockIcon(
                        icon = Icons.Default.Info,
                        label = "Snap",
                        isActive = screenshotOpen,
                        tintColor = Color.LightGray,
                        onClick = { screenshotOpen = !screenshotOpen }
                    )

                    // Vault Launch Icon
                    DockIcon(
                        icon = Icons.Default.Lock,
                        label = "Vault",
                        isActive = vaultOpen,
                        tintColor = Color(0xFFFFD740),
                        onClick = { vaultOpen = !vaultOpen }
                    )
                }
            }
        }

        // --- Cinematic Flash Indicator for screenshots ---
        AnimatedVisibility(
            visible = showScreenFlash,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )
            LaunchedEffect(showScreenFlash) {
                if (showScreenFlash) {
                    kotlinx.coroutines.delay(180)
                    showScreenFlash = false
                }
            }
        }
    }
}

// --- Draggable Resizable Window Core Container ---
@Composable
fun FloatingWindow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    glowColor: Color,
    offset: Offset,
    onOffsetChange: (Offset) -> Unit,
    onClose: () -> Unit,
    initialWidth: Dp = 300.dp,
    initialHeight: Dp = 400.dp,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    var width by remember { mutableStateOf(initialWidth) }
    var height by remember { mutableStateOf(initialHeight) }
    var isMaximized by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .offset {
                if (isMaximized) IntOffset(0, 0)
                else IntOffset(offset.x.roundToInt(), offset.y.roundToInt())
            }
            .size(
                width = if (isMaximized) Dp.Unspecified else width,
                height = if (isMaximized) Dp.Unspecified else height
            )
            .then(if (isMaximized) Modifier.fillMaxSize() else Modifier)
            .padding(8.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFA161A26)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.2.dp, glowColor.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // --- Window Titlebar (Draggable Trigger) ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x33000000))
                        .pointerInput(isMaximized) {
                            if (!isMaximized) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    onOffsetChange(offset + dragAmount)
                                }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = glowColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Monospace
                    )

                    // Control Actions: Maximize & Close
                    IconButton(
                        onClick = { isMaximized = !isMaximized },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isMaximized) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = "Maximize",
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = GlowPink,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // --- Client Window Content Workspace ---
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0x13000000))
                ) {
                    content()
                }

                // --- Minimal Window Resize Handle (Bottom Right Corner) ---
                if (!isMaximized) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .drawBehind {
                                // Draw 3 tiny slant lines on bottom right corner for resize
                                drawLine(
                                    color = glowColor.copy(alpha = 0.4f),
                                    start = Offset(size.width - 30f, size.height),
                                    end = Offset(size.width, size.height - 30f),
                                    strokeWidth = 3f
                                )
                                drawLine(
                                    color = glowColor.copy(alpha = 0.4f),
                                    start = Offset(size.width - 15f, size.height),
                                    end = Offset(size.width, size.height - 15f),
                                    strokeWidth = 3f
                                )
                            }
                            .pointerInput(density) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val dx = with(density) { dragAmount.x.toDp() }
                                    val dy = with(density) { dragAmount.y.toDp() }
                                    width = (width + dx).coerceAtLeast(220.dp)
                                    height = (height + dy).coerceAtLeast(280.dp)
                                }
                            }
                    )
                }
            }
        }
    }
}

// --- Shared Dock Icon Component ---
@Composable
fun DockIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    tintColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    if (isActive) tintColor.copy(alpha = 0.25f) else Color(0x15FFFFFF),
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    1.dp,
                    if (isActive) tintColor else Color(0x22FFFFFF),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) tintColor else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (isActive) tintColor else Color.Gray,
            fontWeight = FontWeight.Bold
        )
    }
}

// ==========================================
// 1. AI CHAT WINDOW CONTENT
// ==========================================
@Composable
fun AiChatWidgetContent(viewModel: WorkspaceViewModel) {
    val history by viewModel.chatHistory.collectAsState()
    val isLoading by viewModel.aiLoading.collectAsState()
    val apiKey by viewModel.groqApiKey.collectAsState()
    var inputMessage by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        if (apiKey.isBlank()) {
            // Unconfigured warning
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Please enter your Groq API Key first from the upper-right settings icon in the main dashboard.",
                    fontSize = 12.sp,
                    color = GlowPink,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            // Chat history list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = false
            ) {
                if (history.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Face,
                                    contentDescription = null,
                                    tint = NeonAqua.copy(alpha = 0.5f),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Groq AI Chat initialized.\nModel: openai/gpt-oss-120b\nAsk anything!",
                                    fontSize = 11.sp,
                                    color = PairColor.TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                items(history) { msg ->
                    val isUser = msg.role == "user"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUser) LightSteel else SteelBlue
                            ),
                            border = BorderStroke(1.dp, if (isUser) NeonAqua.copy(alpha = 0.3f) else GlowLavender.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(
                                topStart = 12.dp,
                                topEnd = 12.dp,
                                bottomStart = if (isUser) 12.dp else 0.dp,
                                bottomEnd = if (isUser) 0.dp else 12.dp
                            ),
                            modifier = Modifier.widthIn(max = 240.dp)
                        ) {
                            Text(
                                text = msg.content,
                                fontSize = 11.sp,
                                color = Color.White,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }

                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SteelBlue),
                                modifier = Modifier.widthIn(max = 120.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(10.dp),
                                        strokeWidth = 1.2.dp,
                                        color = NeonAqua
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "AI is typing...",
                                        fontSize = 10.sp,
                                        color = NeonAqua
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Chat input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputMessage,
                    onValueChange = { inputMessage = it },
                    placeholder = { Text("Ask Groq...", fontSize = 11.sp, color = Color.Gray) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonAqua,
                        unfocusedBorderColor = Color(0x33FFFFFF)
                    ),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        if (inputMessage.isNotBlank()) {
                            viewModel.sendUserMessage(inputMessage)
                            inputMessage = ""
                        }
                    },
                    modifier = Modifier
                        .background(NeonAqua, shape = RoundedCornerShape(12.dp))
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = SpaceBlack,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// 2. CALCULATOR WINDOW CONTENT
// ==========================================
@Composable
fun CalculatorWidgetContent() {
    var display by remember { mutableStateOf("") }
    var expression by remember { mutableStateOf("") }

    val buttons = listOf(
        listOf("C", "DEL", "/", "*"),
        listOf("7", "8", "9", "-"),
        listOf("4", "5", "6", "+"),
        listOf("1", "2", "3", "="),
        listOf("0", ".", "", "")
    )

    fun onButtonClicked(value: String) {
        when (value) {
            "C" -> {
                display = ""
                expression = ""
            }
            "DEL" -> {
                if (display.isNotEmpty()) {
                    display = display.dropLast(1)
                }
            }
            "=", "" -> {
                if (display.isNotBlank() && value == "=") {
                    try {
                        val result = evaluateSimpleExpression(display)
                        expression = "$display ="
                        display = formatResult(result)
                    } catch (e: Exception) {
                        display = "Error"
                    }
                }
            }
            else -> {
                if (display == "Error") display = ""
                display += value
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        // Calculator screen
        Card(
            colors = CardDefaults.cardColors(containerColor = SpaceBlack),
            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.3f)
                .padding(bottom = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = expression,
                    fontSize = 12.sp,
                    color = PairColor.TextSecondary,
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (display.isEmpty()) "0" else display,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlowPink,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Calculator Buttons Grid
        Column(modifier = Modifier.weight(0.7f)) {
            buttons.forEach { row ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    row.forEach { buttonText ->
                        if (buttonText.isNotEmpty()) {
                            val isOperator = "+-*/= C DEL".contains(buttonText)
                            val containerCol = if (buttonText == "=") GlowPink 
                                              else if (isOperator) Color(0xFF232736) 
                                              else Color(0x551E2538)
                            val textCol = if (buttonText == "=") SpaceBlack 
                                          else if (isOperator) GlowPink 
                                          else Color.White

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(vertical = 2.dp)
                                    .background(containerCol, shape = RoundedCornerShape(8.dp))
                                    .clickable { onButtonClicked(buttonText) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = buttonText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textCol
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// Ultra Simple Math Evaluator
private fun evaluateSimpleExpression(expr: String): Double {
    try {
        // Quick regex and simple tokenizer
        val sanitized = expr.replace(" ", "")
        // Simple search for operators by operations prioritised
        val operators = listOf('+', '-', '*', '/')
        // Search operators
        for (op in operators) {
            val idx = sanitized.lastIndexOf(op)
            if (idx != -1 && idx > 0) {
                val left = evaluateSimpleExpression(sanitized.substring(0, idx))
                val right = evaluateSimpleExpression(sanitized.substring(idx + 1))
                return when (op) {
                    '+' -> left + right
                    '-' -> left - right
                    '*' -> left * right
                    '/' -> left / right
                    else -> 0.0
                }
            }
        }
        return sanitized.toDouble()
    } catch (e: Exception) {
        return 0.0
    }
}

private fun formatResult(value: Double): String {
    return if (value % 1 == 0.0) {
        value.toLong().toString()
    } else {
        String.format("%.4f", value).trimEnd('0').trimEnd('.')
    }
}

// ==========================================
// 3. NOTES WINDOW CONTENT
// ==========================================
@Composable
fun NotesWidgetContent(viewModel: WorkspaceViewModel) {
    val notesList by viewModel.notes.collectAsState()
    var isEditing by remember { mutableStateOf(false) }
    var noteTitle by remember { mutableStateOf("") }
    var noteContent by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        if (isEditing) {
            // Edit Screen
            Column(modifier = Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = noteTitle,
                    onValueChange = { noteTitle = it },
                    placeholder = { Text("Note Title", fontSize = 11.sp, color = Color.Gray) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = Color.White),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = noteContent,
                    onValueChange = { noteContent = it },
                    placeholder = { Text("Type note details...", fontSize = 11.sp, color = Color.Gray) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { isEditing = false }) {
                        Text("Cancel", color = GlowPink, fontSize = 11.sp)
                    }

                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = GlowLavender),
                        onClick = {
                            viewModel.addNote(noteTitle, noteContent)
                            noteTitle = ""
                            noteContent = ""
                            isEditing = false
                        }
                    ) {
                        Text("Save Note", color = SpaceBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // Standard List Screen
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Saved Notes", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = { isEditing = true },
                    modifier = Modifier
                        .background(GlowLavender, shape = RoundedCornerShape(8.dp))
                        .size(32.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = SpaceBlack, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (notesList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No notes saved locally.", fontSize = 11.sp, color = PairColor.TextSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(notesList) { note ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SteelBlue),
                            border = BorderStroke(0.5.dp, Color(0x1F00E5FF)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = note.title.ifBlank { "Untitled Note" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = GlowLavender,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = note.content,
                                        fontSize = 10.sp,
                                        color = PairColor.TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.deleteNote(note) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = GlowPink.copy(alpha = 0.8f),
                                        modifier = Modifier.size(14.dp)
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

// ==========================================
// 4. SCREENSHOT WINDOW CONTENT
// ==========================================
@Composable
fun ScreenshotWidgetContent(
    capturedBitmap: Bitmap?,
    onCaptureSimulated: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = NeonAqua,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Screenshot Utility",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Choose whether to simulate a capture inside the application workspace canvas, or trigger the standard device capture.",
            fontSize = 11.sp,
            color = PairColor.TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))

        Button(
            colors = ButtonDefaults.buttonColors(containerColor = NeonAqua),
            onClick = onCaptureSimulated,
            modifier = Modifier.fillMaxWidth().height(42.dp)
        ) {
            Icon(Icons.Default.Star, null, tint = SpaceBlack, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Capture Floating Deck Workspace", color = SpaceBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = SteelBlue),
            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "System Overlay Capture Tip:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = GlowLavender
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "When running in background system mode, clicking the hover capture overlay button instantly minimizes the UI so you can seamlessly capture standard phone screens!",
                    fontSize = 10.sp,
                    color = PairColor.TextPrimary,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

// ==========================================
// 5. SECURE PHOTO VAULT CONTENT
// ==========================================
@Composable
fun VaultWidgetContent(viewModel: WorkspaceViewModel) {
    val unlocked by viewModel.vaultUnlocked.collectAsState()
    val authError by viewModel.vaultAuthError.collectAsState()
    val rawImagesList by viewModel.vaultImages.collectAsState()

    var passcodeEntry by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.hidePhoto(it) }
    }

    if (!unlocked) {
        // Passcode Lock View
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = Color(0xFFFFD740),
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Secured Photo Vault",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Enter secure PIN to access private database",
                fontSize = 11.sp,
                color = PairColor.TextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = passcodeEntry,
                onValueChange = { if (it.length <= 4) passcodeEntry = it },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, textAlign = TextAlign.Center, color = Color.White),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                placeholder = { Text("----", fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFFD740),
                    unfocusedBorderColor = Color(0x33FFFFFF)
                ),
                modifier = Modifier.width(100.dp)
            )

            if (authError) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Incorrect passcode!",
                    color = GlowPink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD740)),
                onClick = {
                    if (viewModel.unlockVault(passcodeEntry)) {
                        passcodeEntry = ""
                    }
                },
                modifier = Modifier.width(120.dp)
            ) {
                Text("Unlock", color = SpaceBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        // Authed Secure Gallery Grid
        var selectedImageForAction by remember { mutableStateOf<VaultImage?>(null) }

        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SECURED PHOTOS (${rawImagesList.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFFD740)
                )

                Row {
                    IconButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .background(Color(0xFFFFD740), shape = RoundedCornerShape(8.dp))
                            .size(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add image", tint = SpaceBlack, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { viewModel.lockVault() },
                        modifier = Modifier
                            .background(LightSteel, shape = RoundedCornerShape(8.dp))
                            .size(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (rawImagesList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Vault is empty.\nClick '+' above to secure a photo.",
                        fontSize = 11.sp,
                        color = PairColor.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(rawImagesList) { vaultImg ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                                .clickable { selectedImageForAction = vaultImg }
                        ) {
                            // Render image from private app directories
                            AsyncImage(
                                model = File(vaultImg.internalPath),
                                contentDescription = "Hidden Vault image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }

        // Action dialog for selected image
        if (selectedImageForAction != null) {
            val img = selectedImageForAction!!
            AlertDialog(
                onDismissRequest = { selectedImageForAction = null },
                containerColor = DeepSteel,
                title = { Text("Secured Photo Actions", fontSize = 14.sp, color = Color.White) },
                text = {
                    Column {
                        Text("Original Name: ${img.originalFileName}", fontSize = 11.sp, color = PairColor.TextSecondary)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Choose whether to permanently delete this photo or unhide restore back to Downloads folder.", fontSize = 12.sp, color = Color.White)
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = NeonAqua),
                        onClick = {
                            viewModel.restorePhoto(img)
                            selectedImageForAction = null
                        }
                    ) {
                        Text("Unhide (Restore)", color = SpaceBlack, fontSize = 11.sp)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.deletePhoto(img)
                            selectedImageForAction = null
                        }
                    ) {
                        Text("Permanent Delete", color = GlowPink, fontSize = 11.sp)
                    }
                }
            )
        }
    }
}

// ==========================================
// SYSTEM SETTINGS OVERLAY PANEL
// ==========================================
@Composable
fun SettingsOverlay(
    viewModel: WorkspaceViewModel,
    onDismiss: () -> Unit
) {
    val currentApiKey by viewModel.groqApiKey.collectAsState()
    val currentPasscode by viewModel.vaultPasscode.collectAsState()

    var apiKeyText by remember { mutableStateOf(currentApiKey) }
    var passcodeText by remember { mutableStateOf(currentPasscode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, null, tint = NeonAqua, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Dashboard Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        containerColor = DeepSteel,
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Groq SDK config
                Text(
                    text = "GROQ API CONFIG",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = NeonAqua
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    placeholder = { Text("Enter Groq Api Key...", fontSize = 11.sp, color = Color.Gray) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = Color.White),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonAqua,
                        unfocusedBorderColor = Color(0x33FFFFFF)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Requires active Groq API Key to access GPT-associated models like 'openai/gpt-oss-120b'. Will hit Groq chat REST endpoints directly.",
                    fontSize = 9.sp,
                    color = PairColor.TextSecondary,
                    lineHeight = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 2. Vault passcode config
                Text(
                    text = "VAULT SECURITY PASSCODE (PIN)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFFD740)
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = passcodeText,
                    onValueChange = { if (it.length <= 4) passcodeText = it },
                    placeholder = { Text("1234", fontSize = 11.sp, color = Color.Gray) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = Color.White),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFFD740),
                        unfocusedBorderColor = Color(0x33FFFFFF)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Define a 4-digit PIN password to secure your personal imported hidden images catalog in local sandbox.",
                    fontSize = 9.sp,
                    color = PairColor.TextSecondary,
                    lineHeight = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = NeonAqua),
                onClick = {
                    viewModel.updateGroqApiKey(apiKeyText)
                    viewModel.updateVaultPasscode(passcodeText)
                    onDismiss()
                }
            ) {
                Text("Apply Updates", color = SpaceBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = GlowPink, fontSize = 11.sp)
            }
        }
    )
}
