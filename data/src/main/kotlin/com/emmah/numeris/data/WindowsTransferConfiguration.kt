package com.emmah.numeris.data

import com.emmah.numeris.core.database.ConnectionEntity
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.SiteIdentity
import com.emmah.numeris.core.model.reportDomain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

internal data class WindowsTransferConfiguration(
    val connections: List<ConnectionEntity>,
    val settings: String,
) {
    val secretKeys: Set<String> get() =
        connections
            .mapNotNull { connection ->
                when (connection.source) {
                    "cloudflare" -> "cloudflare:${connection.id}:api_token"
                    "web_analytics" -> "web_analytics:${connection.id}:api_token"
                    "crux" -> "crux:crux:api_key"
                    "pagespeed" -> "pagespeed:pagespeed:api_key"
                    "bing_webmaster" -> "bing:bing:api_key"
                    else -> null
                }
            }.toSet()

    companion object {
        fun parse(text: String): WindowsTransferConfiguration? {
            val root = Json.parseToJsonElement(text).jsonObject
            val entries = root["connections"] as? JsonArray ?: return null
            root["selectedPeriod"]?.jsonPrimitive?.content?.let { period ->
                require(Period.entries.any { it.name == period }) { "Invalid imported period" }
            }
            root["selectedDomain"]?.jsonPrimitive?.content?.let { domain ->
                require(domain == "all" || SiteIdentity.normalizeDomain(domain) == domain) { "Invalid imported domain" }
            }
            val rows = entries.map { parseConnection(it.jsonObject) }
            require(rows.map { it.id }.distinct().size == rows.size) { "Duplicate imported connection" }
            return WindowsTransferConfiguration(rows, text)
        }

        private fun parseConnection(row: JsonObject): ConnectionEntity {
            val id = row.getValue("id").jsonPrimitive.content
            val source = row.getValue("source").jsonPrimitive.content
            val metadata = row.getValue("config").jsonObject.mapValues { it.value.jsonPrimitive.content }
            val allowedFields =
                when (source) {
                    "cloudflare" -> setOf("domain", "zoneId")
                    "web_analytics" -> setOf("accountId", "siteMappings")
                    "search_console", "bing_webmaster" -> setOf("sites")
                    "crux", "pagespeed", "uptime", "sitemap" -> emptySet()
                    else -> error("Unsupported imported provider")
                }
            require(id.matches(Regex("[A-Za-z0-9._:-]{1,256}")) && id.startsWith("$source:")) {
                "Invalid imported connection identity"
            }
            require(metadata.keys == allowedFields) { "Unsupported imported metadata" }
            if (source == "cloudflare") {
                val domain = metadata.getValue("domain")
                require(SiteIdentity.normalizeDomain(domain) == domain) {
                    "Invalid Cloudflare domain"
                }
                require(metadata.getValue("zoneId").matches(Regex("[A-Za-z0-9_-]{1,128}"))) {
                    "Invalid Cloudflare zone"
                }
            }
            if (source == "web_analytics") metadata.webSites()
            metadata["sites"]?.split(',')?.filter(String::isNotBlank)?.forEach {
                require(reportDomain(it) != null) { "Invalid imported property" }
            }
            val status = row.getValue("status").jsonPrimitive.content
            require(status in setOf("configured", "connected", "disconnected", "error")) { "Invalid connection status" }
            val lastSync = row["lastSync"]?.jsonPrimitive?.contentOrNull
            lastSync?.let(Instant::parse)
            return ConnectionEntity(id, source, status, row.getValue("config").toString(), lastSync)
        }
    }
}
