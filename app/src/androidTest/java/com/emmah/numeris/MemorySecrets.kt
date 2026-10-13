package com.emmah.numeris

import com.emmah.numeris.core.model.SecretKey
import com.emmah.numeris.core.model.SecretStore

internal class MemorySecrets(
    private val afterPut: (SecretKey) -> Unit = {},
) : SecretStore {
    private val values = mutableMapOf<SecretKey, CharArray>()

    override suspend fun put(
        key: SecretKey,
        value: CharArray,
    ) {
        values.put(key, value.copyOf())?.fill('\u0000')
        afterPut(key)
    }

    override suspend fun get(key: SecretKey) = values[key]?.copyOf()

    override suspend fun contains(key: SecretKey) = key in values

    override suspend fun delete(key: SecretKey) {
        values.remove(key)?.fill('\u0000')
    }

    override suspend fun clearAll() {
        values.values.forEach { it.fill('\u0000') }
        values.clear()
    }
}
