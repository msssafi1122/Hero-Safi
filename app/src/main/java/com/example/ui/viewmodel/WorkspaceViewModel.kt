package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppRepository
import com.example.data.Note
import com.example.data.VaultImage
import com.example.service.FloatingWidgetService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppRepository(application)
    private val context = application.applicationContext

    // --- Groq AI State ---
    private val _groqApiKey = MutableStateFlow("")
    val groqApiKey = _groqApiKey.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory = _chatHistory.asStateFlow()

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading = _aiLoading.asStateFlow()

    // --- Vault State ---
    private val _vaultPasscode = MutableStateFlow("1234")
    val vaultPasscode = _vaultPasscode.asStateFlow()

    private val _vaultUnlocked = MutableStateFlow(false)
    val vaultUnlocked = _vaultUnlocked.asStateFlow()

    private val _vaultAuthError = MutableStateFlow(false)
    val vaultAuthError = _vaultAuthError.asStateFlow()

    private val _vaultImportSuccess = MutableStateFlow<Boolean?>(null)
    val vaultImportSuccess = _vaultImportSuccess.asStateFlow()

    val vaultImages: StateFlow<List<VaultImage>> = repository.allVaultImages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // --- Notes State ---
    val notes: StateFlow<List<Note>> = repository.allNotes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // --- Floating Overlay Service State ---
    private val _isOverlayActive = MutableStateFlow(false)
    val isOverlayActive = _isOverlayActive.asStateFlow()

    // --- Navigation / Selected Window ---
    private val _activeWindowId = MutableStateFlow<String?>(null)
    val activeWindowId = _activeWindowId.asStateFlow()

    init {
        // Load initial values
        _groqApiKey.value = repository.getGroqApiKey()
        _vaultPasscode.value = repository.getVaultPasscode()
        _isOverlayActive.value = FloatingWidgetService.isRunning
    }

    // --- API Configuration ---
    fun updateGroqApiKey(key: String) {
        _groqApiKey.value = key
        repository.saveGroqApiKey(key)
    }

    // --- AI Chat Actions ---
    fun sendUserMessage(content: String) {
        if (content.isBlank()) return

        val userMsg = ChatMessage(role = "user", content = content)
        _chatHistory.value = _chatHistory.value + userMsg

        viewModelScope.launch {
            _aiLoading.value = true
            val response = repository.askGroq(content)
            _aiLoading.value = false

            val assistantMsg = ChatMessage(role = "assistant", content = response)
            _chatHistory.value = _chatHistory.value + assistantMsg
        }
    }

    fun clearChat() {
        _chatHistory.value = emptyList()
    }

    // --- Notes Actions ---
    fun addNote(title: String, content: String) {
        if (title.isBlank() && content.isBlank()) return
        viewModelScope.launch {
            val note = Note(title = title, content = content)
            repository.saveNote(note)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    // --- Vault Actions ---
    fun unlockVault(pass: String): Boolean {
        if (pass == _vaultPasscode.value) {
            _vaultUnlocked.value = true
            _vaultAuthError.value = false
            return true
        } else {
            _vaultAuthError.value = true
            return false
        }
    }

    fun lockVault() {
        _vaultUnlocked.value = false
        _vaultAuthError.value = false
    }

    fun updateVaultPasscode(newPass: String) {
        if (newPass.length >= 4) {
            _vaultPasscode.value = newPass
            repository.saveVaultPasscode(newPass)
        }
    }

    fun hidePhoto(uri: Uri) {
        viewModelScope.launch {
            val success = repository.hideImageInVault(uri)
            _vaultImportSuccess.value = success
            if (success) {
                Toast.makeText(context, "Photo hidden securely in Vault", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to import photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deletePhoto(image: VaultImage) {
        viewModelScope.launch {
            repository.deleteImageFromVault(image)
            Toast.makeText(context, "Photo deleted securely", Toast.LENGTH_SHORT).show()
        }
    }

    fun restorePhoto(image: VaultImage) {
        viewModelScope.launch {
            val success = repository.restoreImageFromVault(image)
            if (success) {
                Toast.makeText(context, "Photo restored to Downloads folder", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Failed to restore photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- System Overlay Service Control ---
    fun toggleOverlayService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "Please grant 'Draw over other apps' permission first", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Unable to launch settings. Please search 'Draw over other apps' in Settings.", Toast.LENGTH_LONG).show()
            }
            return
        }

        val intent = Intent(context, FloatingWidgetService::class.java)
        if (FloatingWidgetService.isRunning) {
            context.stopService(intent)
            _isOverlayActive.value = false
            Toast.makeText(context, "Overlay service stopped", Toast.LENGTH_SHORT).show()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            _isOverlayActive.value = true
            Toast.makeText(context, "Overlay Service Started!", Toast.LENGTH_LONG).show()
        }
    }

    fun syncOverlayStatus() {
        _isOverlayActive.value = FloatingWidgetService.isRunning
    }

    fun setActiveWindow(id: String?) {
        _activeWindowId.value = id
    }
}

data class ChatMessage(
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
