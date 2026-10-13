package com.emmah.numeris.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

data class DiscoveredTargetWire(
    val domain: String,
    val id: String,
)

class SourceDiscoveryClient(
    private val http: NumerisHttpClient,
) {
    private companion object {
        const val MAX_PAGES = 10
        const val PAGE_SIZE = 100
    }

    suspend fun cloudflare(
        token: CharArray,
        accountId: String?,
    ): List<DiscoveredTargetWire> {
        val result = mutableListOf<DiscoveredTargetWire>()
        val base =
            if (accountId == null) {
                "https://api.cloudflare.com/client/v4/zones"
            } else {
                "https://api.cloudflare.com/client/v4/accounts/${accountId.trim()}/rum/site_info/list"
            }
        for (page in 1..MAX_PAGES) {
            val response =
                http.execute(
                    "Cloudflare",
                    Request
                        .Builder()
                        .url("$base?page=$page&per_page=$PAGE_SIZE")
                        .header("Authorization", "Bearer ${token.concatToString().removePrefix("Bearer ").trim()}")
                        .get()
                        .build(),
                )
            val root = Json.parseToJsonElement(response.body).jsonObject
            require(root["success"]?.jsonPrimitive?.content == "true") { "Source discovery failed." }
            val rows = root.getValue("result").jsonArray
            result +=
                rows.mapNotNull { value ->
                    val row = value.jsonObject
                    val domain = row[if (accountId == null) "name" else "host"]?.jsonPrimitive?.contentOrNull
                    val id = row[if (accountId == null) "id" else "site_tag"]?.jsonPrimitive?.contentOrNull
                    if (domain.isNullOrBlank() || id.isNullOrBlank()) null else DiscoveredTargetWire(domain, id)
                }
            if (rows.size < PAGE_SIZE) return result
        }
        error("More than 1,000 targets are available. Configure the required targets manually.")
    }

    suspend fun google(token: String): List<String> {
        val response =
            http.execute(
                "Google Search Console",
                Request
                    .Builder()
                    .url("https://www.googleapis.com/webmasters/v3/sites")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build(),
            )
        return Json
            .parseToJsonElement(response.body)
            .jsonObject["siteEntry"]
            ?.jsonArray
            .orEmpty()
            .mapNotNull {
                it.jsonObject["siteUrl"]?.jsonPrimitive?.contentOrNull
            }.distinct()
            .sorted()
    }
}
