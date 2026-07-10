package com.emmah.numeris.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.emmah.numeris.core.model.Period
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val Context.numerisPreferences by preferencesDataStore(name = "numeris-settings")

data class AppPreferences(
    val selectedDomain: String = "all",
    val selectedPeriod: Period = Period.Last7Days,
    val lastPage: String = "overview",
    val backgroundSyncEnabled: Boolean = true,
    val backgroundSyncHours: Int = 12,
    val biometricLockEnabled: Boolean = true,
)

class AppPreferencesRepository(
    private val context: Context,
) {
    val preferences: Flow<AppPreferences> =
        context.numerisPreferences.data.map { values ->
            AppPreferences(
                selectedDomain = values[selectedDomainKey] ?: "all",
                selectedPeriod = values[selectedPeriodKey]?.toPeriod() ?: Period.Last7Days,
                lastPage = values[lastPageKey] ?: "overview",
                backgroundSyncEnabled = values[backgroundSyncEnabledKey] ?: true,
                backgroundSyncHours = values[backgroundSyncHoursKey]?.coerceIn(MIN_SYNC_HOURS, MAX_SYNC_HOURS) ?: 12,
                biometricLockEnabled = values[biometricLockEnabledKey] ?: true,
            )
        }

    suspend fun selectDomain(domain: String) = update(selectedDomainKey, domain)

    suspend fun selectPeriod(period: Period) = update(selectedPeriodKey, period.name)

    suspend fun selectPage(page: String) = update(lastPageKey, page)

    suspend fun setBackgroundSync(
        enabled: Boolean,
        hours: Int,
    ) {
        context.numerisPreferences.edit { values ->
            values[backgroundSyncEnabledKey] = enabled
            values[backgroundSyncHoursKey] = hours.coerceIn(MIN_SYNC_HOURS, MAX_SYNC_HOURS)
        }
    }

    suspend fun setBiometricLock(enabled: Boolean) = update(biometricLockEnabledKey, enabled)

    suspend fun importWindowsSettings(jsonText: String) {
        val values = runCatching { Json.parseToJsonElement(jsonText).jsonObject }.getOrNull() ?: return
        context.numerisPreferences.edit { preferences ->
            values["selectedDomain"]?.jsonPrimitive?.content?.let { preferences[selectedDomainKey] = it }
            values["selectedPeriod"]?.jsonPrimitive?.content?.let { preferences[selectedPeriodKey] = it }
            values["lastPage"]?.jsonPrimitive?.content?.let { preferences[lastPageKey] = it.toAndroidPage() }
        }
    }

    private suspend fun update(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        value: String,
    ) {
        context.numerisPreferences.edit { it[key] = value }
    }

    private suspend fun update(
        key: androidx.datastore.preferences.core.Preferences.Key<Boolean>,
        value: Boolean,
    ) {
        context.numerisPreferences.edit { it[key] = value }
    }

    private fun String.toPeriod(): Period =
        Period.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }
            ?: Period.Last7Days

    private fun String.toAndroidPage(): String =
        when (lowercase()) {
            "dashboard" -> "overview"
            "cloudflare", "googleanalytics" -> "traffic"
            "searchconsole", "bing" -> "search"
            "performance" -> "performance"
            "health" -> "health"
            "sources" -> "sources"
            else -> "overview"
        }

    private companion object {
        const val MIN_SYNC_HOURS = 1
        const val MAX_SYNC_HOURS = 24 * 7
        val selectedDomainKey = stringPreferencesKey("selected_domain")
        val selectedPeriodKey = stringPreferencesKey("selected_period")
        val lastPageKey = stringPreferencesKey("last_page")
        val backgroundSyncEnabledKey = booleanPreferencesKey("background_sync_enabled")
        val backgroundSyncHoursKey = intPreferencesKey("background_sync_hours")
        val biometricLockEnabledKey = booleanPreferencesKey("biometric_lock_enabled")
    }
}
