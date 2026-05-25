package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_images")
data class VaultImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalFileName: String,
    val internalPath: String,
    val dateAdded: Long = System.currentTimeMillis()
)
