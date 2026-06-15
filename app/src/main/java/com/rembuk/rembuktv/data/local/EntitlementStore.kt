package com.rembuk.rembuktv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rembuk.rembuktv.domain.model.Entitlement
import com.rembuk.rembuktv.domain.model.EntitlementStatus
import com.rembuk.rembuktv.domain.model.RemoteConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.entitlementStore: DataStore<Preferences> by preferencesDataStore(name = "entitlement")

/** Persists the last entitlement, remote config and catalog version (offline-friendly). */
@Singleton
class EntitlementStore @Inject constructor(@ApplicationContext context: Context) {

    private val store = context.entitlementStore

    private object Keys {
        val CATALOG = stringPreferencesKey("catalog_version")
        val STATUS = stringPreferencesKey("ent_status")
        val ENTITLED = booleanPreferencesKey("ent_entitled")
        val EXPIRES = longPreferencesKey("ent_expires")
        val SERVERNOW = longPreferencesKey("ent_servernow")
        val CFG_WEB = stringPreferencesKey("cfg_web")
        val CFG_PROMO = stringPreferencesKey("cfg_promo")
        val CFG_MINVER = stringPreferencesKey("cfg_minver")
    }

    suspend fun catalogVersion(): String? = store.data.first()[Keys.CATALOG]

    suspend fun saveCatalogVersion(v: String?) = edit { p ->
        if (v == null) p.remove(Keys.CATALOG) else p[Keys.CATALOG] = v
    }

    fun observeEntitlement(): Flow<Entitlement> = store.data.map { p ->
        Entitlement(
            status = p[Keys.STATUS]?.let { runCatching { EntitlementStatus.valueOf(it) }.getOrNull() }
                ?: EntitlementStatus.FREE,
            entitled = p[Keys.ENTITLED] ?: false,
            expiresAtEpochMs = p[Keys.EXPIRES]?.takeIf { it > 0 },
            serverNowEpochMs = p[Keys.SERVERNOW]?.takeIf { it > 0 },
        )
    }

    suspend fun saveEntitlement(e: Entitlement) = edit { p ->
        p[Keys.STATUS] = e.status.name
        p[Keys.ENTITLED] = e.entitled
        p[Keys.EXPIRES] = e.expiresAtEpochMs ?: 0L
        p[Keys.SERVERNOW] = e.serverNowEpochMs ?: 0L
    }

    fun observeConfig(): Flow<RemoteConfig> = store.data.map { p ->
        RemoteConfig(
            websiteUrl = p[Keys.CFG_WEB] ?: "",
            promoVideoUrl = p[Keys.CFG_PROMO] ?: "",
            minAppVersion = p[Keys.CFG_MINVER] ?: "1.0.0",
        )
    }

    suspend fun saveConfig(c: RemoteConfig) = edit { p ->
        p[Keys.CFG_WEB] = c.websiteUrl
        p[Keys.CFG_PROMO] = c.promoVideoUrl
        p[Keys.CFG_MINVER] = c.minAppVersion
    }

    private suspend inline fun edit(crossinline block: (MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }
}
