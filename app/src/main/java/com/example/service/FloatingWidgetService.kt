package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.example.data.AppRepository
import com.example.ui.screens.AiChatWidgetContent
import com.example.ui.screens.CalculatorWidgetContent
import com.example.ui.screens.NotesWidgetContent
import com.example.ui.screens.ScreenshotWidgetContent
import com.example.ui.theme.NeonAqua
import com.example.ui.theme.DeepSteel
import com.example.ui.theme.GlowLavender
import com.example.ui.theme.GlowPink
import com.example.ui.theme.SpaceBlack
import com.example.ui.viewmodel.ChatMessage
import com.example.ui.viewmodel.WorkspaceViewModel

class FloatingWidgetService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    // --- Lifecycle Boilerplate for Compose inside Service ---
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var overlayPanelView: View? = null

    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var panelParams: WindowManager.LayoutParams

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var isExpanded = false

    companion object {
        var isRunning = false
        private const val NOTIFICATION_ID = 4053
        private const val CHANNEL_ID = "floating_deck_service_channel"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        isRunning = true

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // Create overlay bubble and panel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            setupBubble()
            setupExpandedPanel()
        } else {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        isRunning = false

        bubbleView?.let { windowManager.removeView(it) }
        overlayPanelView?.let { windowManager.removeView(it) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Deck System overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps overlay bubble accessible on home and over other apps."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val title = "Floating Tool Deck Active"
        val text = "Hover bubble is active. Tap to expand AI, Notes, and Calculator."

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setPriority(Notification.PRIORITY_LOW)
                .setOngoing(true)
                .build()
        }
    }

    // --- Set up the compact hovering circular bubble trigger ---
    private fun setupBubble() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingWidgetService)
            setViewTreeViewModelStoreOwner(this@FloatingWidgetService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWidgetService)
            setContent {
                BubbleWidget()
            }
        }

        bubbleView = composeView

        // Touch Listener for Draggability & Shifting Position
        composeView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    bubbleParams.x = initialX + deltaX
                    bubbleParams.y = initialY + deltaY
                    windowManager.updateViewLayout(view, bubbleParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Quick Tap Detection (barely shifted)
                    val deltaX = Math.abs(event.rawX - initialTouchX)
                    val deltaY = Math.abs(event.rawY - initialTouchY)
                    if (deltaX < 10 && deltaY < 10) {
                        togglePanelExpansion()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubbleView, bubbleParams)
    }

    // --- Set up the expanded full tool overlay panel window ---
    private fun setupExpandedPanel() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Expanded Panel can capture keyboard focus
        panelParams = WindowManager.LayoutParams(
            dpToPx(320),
            dpToPx(460),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            x = 0
            y = 0
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingWidgetService)
            setViewTreeViewModelStoreOwner(this@FloatingWidgetService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWidgetService)
            setContent {
                // Instantiating a specific viewmodel instance bounds for service
                val app = application as android.app.Application
                val serviceViewModel = remember { WorkspaceViewModel(app) }
                OverlayPanelContent(serviceViewModel)
            }
        }

        overlayPanelView = composeView
    }

    private fun togglePanelExpansion() {
        if (isExpanded) {
            // Collapse panel back to bubble
            overlayPanelView?.let { windowManager.removeView(it) }
            bubbleView?.visibility = View.VISIBLE
            isExpanded = false
        } else {
            // Expand panel, hide bubble
            bubbleView?.visibility = View.GONE
            windowManager.addView(overlayPanelView, panelParams)
            isExpanded = true
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    // --- Bubble UI Composable ---
    @Composable
    fun BubbleWidget() {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(SpaceBlack.copy(alpha = 0.9f), shape = CircleShape)
                .border(2.dp, NeonAqua, shape = CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NeonAqua.copy(alpha = 0.15f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Expand Floating Deck",
                    tint = NeonAqua,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }

    // --- Overlay Full Panel Content Composable ---
    @Composable
    fun OverlayPanelContent(viewModel: WorkspaceViewModel) {
        var selectedTab by remember { mutableStateOf("AI") }
        var isHidingForScreenshot by remember { mutableStateOf(false) }

        if (isHidingForScreenshot) {
            // Display empty spacer while hiding
            Spacer(modifier = Modifier.size(1.dp))
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = SpaceBlack.copy(alpha = 0.95f)),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.2.dp, NeonAqua.copy(alpha = 0.7f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Bar Title
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x33000000))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Menu, null, tint = NeonAqua, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "DECK: Overlay Window",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        IconButton(
                            onClick = { togglePanelExpansion() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, null, tint = GlowPink, modifier = Modifier.size(16.dp))
                        }
                    }

                    // Content Viewport Switcher
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (selectedTab) {
                            "AI" -> AiChatWidgetContent(viewModel)
                            "Calc" -> CalculatorWidgetContent()
                            "Notes" -> NotesWidgetContent(viewModel)
                            "Snap" -> OverlayScreenshotWidget {
                                // Trigger Screenshot sequence (hide self, wait, show Toast, show self)
                                isHidingForScreenshot = true
                                overlayPanelView?.visibility = View.GONE
                                Handler(Looper.getMainLooper()).postDelayed({
                                    Toast.makeText(this@FloatingWidgetService, "Please trigger system screenshot key combo!", Toast.LENGTH_SHORT).show()
                                    overlayPanelView?.visibility = View.VISIBLE
                                    isHidingForScreenshot = false
                                }, 800)
                            }
                        }
                    }

                    // Bottom Navigation Grid
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DeepSteel),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CompactTabButton(
                                icon = Icons.Default.Face,
                                label = "AI",
                                isSelected = selectedTab == "AI",
                                color = NeonAqua,
                                onClick = { selectedTab = "AI" }
                            )

                            CompactTabButton(
                                icon = Icons.Default.Build,
                                label = "Calc",
                                isSelected = selectedTab == "Calc",
                                color = GlowPink,
                                onClick = { selectedTab = "Calc" }
                            )

                            CompactTabButton(
                                icon = Icons.Default.Create,
                                label = "Notes",
                                isSelected = selectedTab == "Notes",
                                color = GlowLavender,
                                onClick = { selectedTab = "Notes" }
                            )

                            CompactTabButton(
                                icon = Icons.Default.Info,
                                label = "Snap",
                                isSelected = selectedTab == "Snap",
                                color = Color.LightGray,
                                onClick = { selectedTab = "Snap" }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun CompactTabButton(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        label: String,
        isSelected: Boolean,
        color: Color,
        onClick: () -> Unit
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp, horizontal = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) color else Color.Gray,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) color else Color.Gray
            )
        }
    }

    @Composable
    fun OverlayScreenshotWidget(onTriggerHide: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Info, null, tint = Color.LightGray, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text("Overlay Capture Utility", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Clicking below hides this overlay interface for a brief delay so you can snap underlying background applications cleanly.", textAlign = TextAlign.Center, fontSize = 10.sp, color = Color.Gray, lineHeight = 14.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = NeonAqua),
                onClick = onTriggerHide,
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Text("Hide Overlay & Capture", color = SpaceBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
