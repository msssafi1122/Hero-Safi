package com.example.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.ui.theme.PairColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

class AppRepository(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val noteDao = database.noteDao()
    private val vaultImageDao = database.vaultImageDao()

    private val sharedPrefs = context.getSharedPreferences("floating_app_prefs", Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // --- Preferences Constants ---
    companion object {
        private const val KEY_GROQ_API_KEY = "groq_api_key"
        private const val KEY_VAULT_PASSCODE = "vault_passcode"
    }

    // --- Notes Management ---
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()

    suspend fun saveNote(note: Note): Long = withContext(Dispatchers.IO) {
        noteDao.insertNote(note)
    }

    suspend fun deleteNote(note: Note) = withContext(Dispatchers.IO) {
        noteDao.deleteNote(note)
    }

    // --- Settings Preferences ---
    fun getGroqApiKey(): String {
        return sharedPrefs.getString(KEY_GROQ_API_KEY, "") ?: ""
    }

    fun saveGroqApiKey(key: String) {
        sharedPrefs.edit().putString(KEY_GROQ_API_KEY, key).apply()
    }

    fun getVaultPasscode(): String {
        return sharedPrefs.getString(KEY_VAULT_PASSCODE, "1234") ?: "1234"
    }

    fun saveVaultPasscode(passcode: String) {
        sharedPrefs.edit().putString(KEY_VAULT_PASSCODE, passcode).apply()
    }

    // --- Vault Photo Management ---
    val allVaultImages: Flow<List<VaultImage>> = vaultImageDao.getAllImages()

    suspend fun hideImageInVault(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val originalName = getFileNameFromUri(uri) ?: "imported_image_${System.currentTimeMillis()}.jpg"
            val vaultDir = File(context.filesDir, "vault")
            if (!vaultDir.exists()) {
                vaultDir.mkdirs()
            }

            val uniqueName = "hidden_${UUID.randomUUID()}_${System.currentTimeMillis()}"
            val destFile = File(vaultDir, uniqueName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (destFile.exists() && destFile.length() > 0) {
                val vaultImage = VaultImage(
                    originalFileName = originalName,
                    internalPath = destFile.absolutePath
                )
                vaultImageDao.insertImage(vaultImage)
                return@withContext true
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteImageFromVault(image: VaultImage) = withContext(Dispatchers.IO) {
        try {
            val file = File(image.internalPath)
            if (file.exists()) {
                file.delete()
            }
            vaultImageDao.deleteImage(image)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun restoreImageFromVault(image: VaultImage): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(image.internalPath)
            if (!file.exists()) return@withContext false

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null && !downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            val restoredFile = File(downloadsDir, "Restored_${image.originalFileName}")
            file.inputStream().use { input ->
                restoredFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (restoredFile.exists()) {
                file.delete()
                vaultImageDao.deleteImage(image)
                return@withContext true
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return name
    }

    // --- AI Chat with Groq Integration ---
    suspend fun askGroq(message: String): String = withContext(Dispatchers.IO) {
        val apiKey = getGroqApiKey()
        if (apiKey.isBlank()) {
            return@withContext "Error: Groq API Key has not been configured in Settings. Please navigate to the dashboard settings to add your Groq API Key."
        }

        try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestJson = JSONObject().apply {
                put("model", "openai/gpt-oss-120b")
                val messagesArray = okhttp3.internal.concurrent.TaskRunner.logger.let {
                    val arr = org.json.JSONArray()
                    arr.put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You are an intelligent AI chatbot built inside a floating widget workspace. Help the user concisely and friendly.")
                    })
                    arr.put(JSONObject().apply {
                        put("role", "user")
                        put("content", message)
                    })
                    arr
                }
                put("messages", messagesArray)
                put("temperature", 0.7)
            }

            val requestBody = requestJson.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        JSONObject(bodyStr).getJSONObject("error").getString("message")
                    } catch (e: Exception) {
                        "HTTP ${response.code}"
                    }
                    return@withContext "Error from Groq API: $errorMsg"
                }

                val jsonResponse = JSONObject(bodyStr)
                return@withContext jsonResponse
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "Connection Error: Unable to reach Groq servers. ${e.localizedMessage ?: "Please confirm your internet connection."}"
        }
    }
}
