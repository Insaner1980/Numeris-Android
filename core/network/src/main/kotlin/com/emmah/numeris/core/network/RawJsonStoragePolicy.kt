package com.emmah.numeris.core.network

object RawJsonStoragePolicy {
    const val MAX_RAW_JSON_CHARS = 200_000
    const val MAX_DETAIL_CHARS = 40_000

    private val sensitiveJsonValue =
        Regex(
            pattern =
                """(?i)(\"(?:(?:access|refresh)[_-]?token|api[_-]?key|""" +
                    """client[_-]?secret|password|authorization)\"\s*:\s*)\"[^\"]*\"""",
        )
    private val bearerValue = Regex("(?i)Bearer\\s+[a-z0-9._~+/=-]+")
    private val sensitiveQueryValue = Regex("(?i)([?&](?:key|token|api_key|access_token)=)[^&#\\s]+")

    fun redactAndLimit(
        value: String,
        maxChars: Int = MAX_RAW_JSON_CHARS,
    ): String =
        sensitiveQueryValue
            .replace(
                bearerValue.replace(
                    sensitiveJsonValue.replace(value) { match -> match.groupValues[1] + "\"[REDACTED]\"" },
                    "Bearer [REDACTED]",
                ),
            ) { match -> match.groupValues[1] + "[REDACTED]" }
            .take(maxChars)
}
