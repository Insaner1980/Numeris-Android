package com.emmah.numeris.data

import com.emmah.numeris.core.database.ConnectionEntity
import com.emmah.numeris.core.database.WebAnalyticsSiteEntity
import com.emmah.numeris.core.model.SaveConnectionCommand
import com.emmah.numeris.core.model.SiteIdentity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun SaveConnectionCommand.validateCloudflareDomain(connections: List<ConnectionEntity>) {
    val domain = SiteIdentity.normalizeDomain(metadata["domain"].orEmpty())
    connections.forEach { connection ->
        val configuredDomain = SiteIdentity.normalizeDomain(connection.metadata()["domain"].orEmpty())
        if (connection.id == id.value) {
            require(domain == configuredDomain) { "Create a new connection to use a different Cloudflare domain" }
        } else {
            require(domain != configuredDomain) { "A Cloudflare connection already exists for this domain" }
        }
    }
}

internal fun ConnectionEntity.metadata(): Map<String, String> =
    runCatching {
        Json.parseToJsonElement(config.orEmpty()).jsonObject.mapValues { (_, value) ->
            if (value is JsonArray) {
                value.joinToString(
                    ",",
                ) { it.jsonPrimitive.content }
            } else {
                value.jsonPrimitive.content
            }
        }
    }.getOrDefault(emptyMap())

internal fun ConnectionEntity.configuredSites(): List<String> =
    metadata()["sites"]
        .orEmpty()
        .split(',')
        .map(String::trim)
        .filter(String::isNotBlank)

internal fun Map<String, String>.webSites(): List<WebAnalyticsSiteEntity> =
    this["siteMappings"]
        .orEmpty()
        .lines()
        .filter(String::isNotBlank)
        .map { line ->
            val parts = line.trim().split('=', limit = 2)
            require(parts.size == 2 && parts[1].matches(Regex("[a-zA-Z0-9_-]{1,128}"))) { "Invalid site mapping" }
            WebAnalyticsSiteEntity(SiteIdentity.normalizeDomain(parts[0]), parts[1], "")
        }.also { sites ->
            require(sites.map { it.domain }.distinct().size == sites.size) { "Duplicate site mapping" }
        }
