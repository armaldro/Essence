package com.armaldro.essence.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.agentStore by preferencesDataStore(name = "agent")

@Serializable
data class Memory(val id: String = UUID.randomUUID().toString(), val text: String, val createdAt: Long = System.currentTimeMillis())

@Serializable
data class AgentCard(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val prompt: String,
    val output: String = "",
    val updatedAt: Long = 0,
)

const val DEFAULT_MODEL = "claude-opus-5-5"

/**
 * Agent settings, memories and home-screen cards. The API key is encrypted with an
 * Android Keystore key before it is written, so it never sits on disk in plain text.
 */
class AgentStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val apiKeyKey = stringPreferencesKey("api_key")
    private val modelKey = stringPreferencesKey("model")
    private val memoriesKey = stringPreferencesKey("memories")
    private val cardsKey = stringPreferencesKey("cards")

    private val data get() = context.agentStore.data

    val hasApiKey: Flow<Boolean> = data.map { !it[apiKeyKey].isNullOrEmpty() }
    val model: Flow<String> = data.map { it[modelKey]?.takeIf(String::isNotBlank) ?: DEFAULT_MODEL }
    val memories: Flow<List<Memory>> = data.map { decodeList<Memory>(it, memoriesKey) }
    val cards: Flow<List<AgentCard>> = data.map { decodeList<AgentCard>(it, cardsKey) }

    suspend fun apiKey(): String? = data.first()[apiKeyKey]?.let { runCatching { decrypt(it) }.getOrNull() }

    suspend fun setApiKey(value: String) {
        context.agentStore.edit { prefs ->
            if (value.isBlank()) prefs.remove(apiKeyKey) else prefs[apiKeyKey] = encrypt(value.trim())
        }
    }

    suspend fun setModel(value: String) {
        context.agentStore.edit { it[modelKey] = value.trim() }
    }

    suspend fun addMemory(text: String) = editList<Memory>(memoriesKey) { it + Memory(text = text.trim()) }

    suspend fun deleteMemory(id: String) = editList<Memory>(memoriesKey) { list -> list.filterNot { it.id == id } }

    suspend fun saveCard(card: AgentCard) = editList<AgentCard>(cardsKey) { list ->
        if (list.any { it.id == card.id }) list.map { if (it.id == card.id) card else it } else list + card
    }

    suspend fun deleteCard(id: String) = editList<AgentCard>(cardsKey) { list -> list.filterNot { it.id == id } }

    private inline fun <reified T> decodeList(prefs: Preferences, key: Preferences.Key<String>): List<T> =
        prefs[key]?.let { runCatching { json.decodeFromString<List<T>>(it) }.getOrNull() } ?: emptyList()

    private suspend inline fun <reified T> editList(key: Preferences.Key<String>, crossinline change: (List<T>) -> List<T>) {
        context.agentStore.edit { prefs -> prefs[key] = json.encodeToString(change(decodeList<T>(prefs, key))) }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val sealed = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, sealed, 0, IV_SIZE))
        }
        return String(cipher.doFinal(sealed, IV_SIZE, sealed.size - IV_SIZE))
    }

    private companion object {
        const val KEY_ALIAS = "essence_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
