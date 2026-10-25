package com.emmah.numeris.data

import androidx.room3.useReaderConnection
import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.model.SecretKey
import com.emmah.numeris.core.model.SecretStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class WindowsConfigurationTransfer(
    private val database: NumerisDatabase,
    private val secrets: SecretStore,
    private val preferences: AppPreferencesRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    @Suppress("TooGenericExceptionCaught")
    internal suspend fun replace(
        id: String,
        configuration: WindowsTransferConfiguration,
        importedSecrets: Map<String, CharArray>,
        commitReports: suspend () -> Unit,
    ) {
        recover()
        require(importedSecrets.keys.all { it in configuration.secretKeys }) { "Unassigned imported secret" }
        val journal = createJournal(id, importedSecrets.keys)
        try {
            secrets.put(JOURNAL_KEY, journal)
            importedSecrets.forEach { (key, value) -> secrets.put(SecretKey(key), value) }
            preferences.importWindowsSettings(configuration.settings)
            commitReports()
            secrets.delete(JOURNAL_KEY)
        } catch (failure: Exception) {
            withContext(NonCancellable) { recover() }
            throw failure
        } finally {
            journal.fill('\u0000')
        }
    }

    suspend fun recover() =
        withContext(ioDispatcher) {
            val bytes = secrets.get(JOURNAL_KEY) ?: return@withContext
            try {
                val journal = Json.parseToJsonElement(String(bytes)).jsonObject
                val id = journal.getValue("id").jsonPrimitive.content
                val committed =
                    database.useReaderConnection { connection ->
                        connection.usePrepared(
                            "SELECT value FROM meta WHERE `key`='android_transfer_id'",
                        ) { statement ->
                            statement.step() && statement.getText(0) == id
                        }
                    }
                if (!committed) {
                    restoreSecrets(journal.getValue("secrets").jsonObject)
                    preferences.importWindowsSettings(journal.getValue("preferences").toString())
                }
                secrets.delete(JOURNAL_KEY)
            } finally {
                bytes.fill('\u0000')
            }
        }

    private suspend fun createJournal(
        id: String,
        keys: Set<String>,
    ): CharArray {
        val previous = keys.associateWith { secrets.get(SecretKey(it)) }
        try {
            val selected = preferences.preferences.first()
            return buildJsonObject {
                put("id", id)
                put(
                    "secrets",
                    JsonObject(
                        previous.mapValues { (_, value) ->
                            if (value == null) JsonNull else JsonPrimitive(String(value))
                        },
                    ),
                )
                put(
                    "preferences",
                    buildJsonObject {
                        put("selectedDomain", selected.selectedDomain)
                        put("selectedPeriod", selected.selectedPeriod.name)
                        put("lastPage", selected.lastPage)
                    },
                )
            }.toString().toCharArray()
        } finally {
            previous.values.forEach { it?.fill('\u0000') }
        }
    }

    private suspend fun restoreSecrets(previous: JsonObject) {
        previous.forEach { (key, value) ->
            val text = value.jsonPrimitive.contentOrNull
            if (text == null) {
                secrets.delete(SecretKey(key))
            } else {
                val secret = text.toCharArray()
                try {
                    secrets.put(SecretKey(key), secret)
                } finally {
                    secret.fill('\u0000')
                }
            }
        }
    }

    private companion object {
        val JOURNAL_KEY = SecretKey("numeris:windows-transfer:journal")
    }
}
