package com.emmah.numeris.core.model

import java.net.URI

data class SiteIdentity(
    val domain: String,
    val originUrl: String,
    val homePageUrl: String,
) {
    companion object {
        private val hostPattern =
            Regex(
                pattern = "^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$",
                option = RegexOption.IGNORE_CASE,
            )

        fun fromDomainOrUrl(value: String): SiteIdentity {
            val domain = normalizeDomain(value)
            return SiteIdentity(domain, "https://$domain", "https://$domain/")
        }

        fun normalizeDomain(value: String): String {
            val trimmed = value.trim()
            require(trimmed.isNotBlank()) { "Domain or URL is required" }

            val host =
                parseHttpUri(trimmed)?.host ?: trimmed.trimEnd('/').also {
                    require('/' !in it && ':' !in it) { "Domain must be a bare host name" }
                }
            val normalized = host.trim().trimEnd('.').lowercase()
            require(hostPattern.matches(normalized)) { "Domain must be a valid host name" }
            return normalized
        }

        fun normalizeOriginUrl(value: String): String = fromDomainOrUrl(value).originUrl

        fun normalizeHomePageUrl(value: String): String = fromDomainOrUrl(value).homePageUrl

        fun normalizeHttpsPageUrl(value: String): String {
            val uri = runCatching { URI(value.trim()) }.getOrNull()
            val hasHttpsScheme = uri?.scheme.equals("https", ignoreCase = true)
            val isValid = uri?.isAbsolute == true && hasHttpsScheme && !uri.host.isNullOrBlank()
            require(isValid) {
                "URL must be an absolute https:// URL"
            }
            require(uri.rawUserInfo == null) { "Target URLs must not contain credentials" }
            require(
                uri.rawQuery.orEmpty().split('&').none { part ->
                    java.net.URLDecoder
                        .decode(part.substringBefore('='), "UTF-8")
                        .lowercase()
                        .replace("_", "")
                        .replace("-", "") in
                        secretQueryNames
                },
            ) { "Target URLs must not contain secret query parameters" }
            val path = uri.rawPath.ifBlank { "/" }
            val normalizedPath =
                if (!path.endsWith('/') && '.' !in path.substringAfterLast('/')) "$path/" else path
            val authority = uri.host.lowercase() + if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
            return "https://$authority$normalizedPath" + uri.rawQuery?.let { "?$it" }.orEmpty()
        }

        fun pagePath(value: String): String {
            val raw = value.trim()
            val path = parseHttpUri(raw)?.rawPath ?: raw.substringBefore('?').substringBefore('#')
            val canonical =
                path.split('/').joinToString("/") { segment ->
                    val decoded = java.net.URLDecoder.decode(segment.replace("+", "%2B"), "UTF-8")
                    java.net.URLEncoder
                        .encode(decoded, "UTF-8")
                        .replace("+", "%20")
                        .replace("%7E", "~")
                }
            val normalized = if (canonical.startsWith('/')) canonical else "/$canonical"
            return if (!normalized.endsWith('/') &&
                '.' !in normalized.substringAfterLast('/')
            ) {
                "$normalized/"
            } else {
                normalized
            }
        }

        private val secretQueryNames =
            setOf(
                "key",
                "apikey",
                "token",
                "accesstoken",
                "refreshtoken",
                "idtoken",
                "clientsecret",
                "secret",
                "password",
                "authorization",
                "code",
            )

        private fun parseHttpUri(value: String): URI? {
            val uri = runCatching { URI(value) }.getOrNull()
            if (uri == null || !uri.isAbsolute) return null
            require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
                "URL must use http:// or https:// and include a host"
            }
            require(!uri.host.isNullOrBlank()) { "URL must use http:// or https:// and include a host" }
            return uri
        }
    }
}

object PerformanceUrl {
    fun normalizeOrigin(value: String): String = SiteIdentity.normalizeOriginUrl(value)

    fun normalizePageUrl(value: String): String = SiteIdentity.normalizeHttpsPageUrl(value)
}
