package com.emmah.numeris.core.network

import com.emmah.numeris.core.model.SiteIdentity
import okhttp3.Request
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

class SitemapClient(
    private val http: NumerisHttpClient,
) {
    suspend fun discoverUrls(domainOrUrl: String): List<String> {
        val site = SiteIdentity.fromDomainOrUrl(domainOrUrl)
        val origin = URI(site.originUrl)
        val indexUri = origin.resolve("/sitemap-index.xml")
        val childSitemaps = parseLocations(fetch(indexUri, origin))
        require(childSitemaps.isNotEmpty()) { "No child sitemaps were found for ${site.domain}." }
        val urls = mutableSetOf<String>()
        childSitemaps.forEach { childValue ->
            val child = validateUri(origin, URI(childValue.trim()), "Child sitemap URL")
            urls += parseLocations(fetch(child, origin))
        }
        return urls.sorted()
    }

    // These fixed XML feature identifiers disable external entities; they are not fetched URLs.
    @Suppress("kotlin:S5332")
    internal fun parseLocations(xml: String): List<String> {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
                setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
            }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val nodes = document.getElementsByTagNameNS("*", "loc")
        return buildList {
            repeat(nodes.length) { index ->
                nodes
                    .item(index)
                    .textContent
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let(::add)
            }
        }
    }

    private suspend fun fetch(
        initial: URI,
        allowedOrigin: URI,
    ): String {
        var current = validateUri(allowedOrigin, initial, "Sitemap URL")
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val result =
                http.execute(
                    source = "Sitemap",
                    request =
                        Request
                            .Builder()
                            .url(current.toString())
                            .get()
                            .build(),
                    acceptRedirect = true,
                )
            if (result.status !in REDIRECT_MIN..REDIRECT_MAX) return result.body
            require(redirectCount < MAX_REDIRECTS) { "Sitemap request exceeded the redirect limit." }
            val location =
                result.headers.entries
                    .firstOrNull { it.key.equals("Location", ignoreCase = true) }
                    ?.value
                    ?.firstOrNull()
                    ?: error("Sitemap redirect target is invalid.")
            current = validateUri(allowedOrigin, current.resolve(location), "Sitemap redirect URL")
        }
        error("Sitemap request exceeded the redirect limit.")
    }

    internal fun validateUri(
        expectedOrigin: URI,
        candidate: URI,
        label: String,
    ): URI {
        require(candidate.isAbsolute && candidate.scheme.equals("https", ignoreCase = true)) {
            "$label must use https."
        }
        require(candidate.userInfo.isNullOrEmpty()) { "$label must not contain credentials." }
        require(sameOrigin(expectedOrigin, candidate)) { "$label must stay on the monitored origin." }
        require(!isBlockedIpLiteral(candidate.host)) { "$label must not target a local or private address." }
        return candidate
    }

    private fun sameOrigin(
        expected: URI,
        candidate: URI,
    ): Boolean =
        expected.scheme.equals(candidate.scheme, ignoreCase = true) &&
            expected.host.equals(candidate.host, ignoreCase = true) &&
            effectivePort(expected) == effectivePort(candidate)

    private fun effectivePort(uri: URI): Int = if (uri.port >= 0) uri.port else HTTPS_PORT

    private fun isBlockedIpLiteral(host: String?): Boolean =
        if (host.isNullOrBlank() || !looksLikeIpLiteral(host)) {
            false
        } else {
            runCatching { InetAddress.getByName(host) }.getOrNull()?.let(::isBlockedAddress) ?: true
        }

    private fun isBlockedAddress(address: InetAddress): Boolean =
        address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress ||
            when (address) {
                is Inet4Address -> (address.address[0].toInt() and UNSIGNED_BYTE_MASK) >= MULTICAST_IPV4_MIN
                is Inet6Address -> (address.address[0].toInt() and IPV6_PREFIX_MASK) == UNIQUE_LOCAL_IPV6_PREFIX
                else -> true
            }

    private fun looksLikeIpLiteral(host: String): Boolean = ':' in host || host.all { it.isDigit() || it == '.' }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val REDIRECT_MIN = 300
        const val REDIRECT_MAX = 399
        const val HTTPS_PORT = 443
        const val UNSIGNED_BYTE_MASK = 0xff
        const val MULTICAST_IPV4_MIN = 224
        const val IPV6_PREFIX_MASK = 0xfe
        const val UNIQUE_LOCAL_IPV6_PREFIX = 0xfc
    }
}
