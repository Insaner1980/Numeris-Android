package com.emmah.numeris

import com.emmah.numeris.core.model.SecretKey
import com.emmah.numeris.core.model.SecretStore

internal class FixedSecrets(
    private val value: String,
) : SecretStore {
    override suspend fun get(key: SecretKey): CharArray = value.toCharArray()

    override suspend fun contains(key: SecretKey): Boolean = true

    override suspend fun put(
        key: SecretKey,
        value: CharArray,
    ) = Unit

    override suspend fun delete(key: SecretKey) = Unit

    override suspend fun clearAll() = Unit
}
