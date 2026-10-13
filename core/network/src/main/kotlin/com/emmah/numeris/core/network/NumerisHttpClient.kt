package com.emmah.numeris.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.Duration

class NumerisHttpClient(
    private val client: OkHttpClient = defaultClient(),
) {
    suspend fun execute(
        source: String,
        request: Request,
        acceptRedirect: Boolean = false,
    ): HttpResult =
        withContext(Dispatchers.IO) {
            val response =
                try {
                    client.newCall(request).execute()
                } catch (_: IOException) {
                    throw ApiErrors.fromIOException(source)
                }
            response.use { toResult(source, it, acceptRedirect) }
        }

    private fun toResult(
        source: String,
        response: Response,
        acceptRedirect: Boolean,
    ): HttpResult {
        if (!response.isSuccessful && !(acceptRedirect && response.code in REDIRECT_MIN..REDIRECT_MAX)) {
            val retryAfter = response.header("Retry-After")?.toLongOrNull()
            val errorCode =
                runCatching { ApiErrors.errorCode(response.peekBody(ERROR_PEEK_BYTES).string()) }.getOrNull()
            throw ApiErrors.fromStatus(source, response.code, retryAfter, errorCode)
        }
        val body = response.body.string()
        require(body.length <= MAX_RESPONSE_CHARS) { "$source returned an unexpectedly large response." }
        return HttpResult(response.code, body, response.headers.toMultimap())
    }

    data class HttpResult(
        val status: Int,
        val body: String,
        val headers: Map<String, List<String>>,
    )

    companion object {
        private const val MAX_RESPONSE_CHARS = 2_000_000
        private const val ERROR_PEEK_BYTES = 16_384L
        private const val REDIRECT_MIN = 300
        private const val REDIRECT_MAX = 399
        private const val CONNECT_TIMEOUT_SECONDS = 12L
        private const val IO_TIMEOUT_SECONDS = 20L

        fun defaultClient(): OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .readTimeout(Duration.ofSeconds(IO_TIMEOUT_SECONDS))
                .writeTimeout(Duration.ofSeconds(IO_TIMEOUT_SECONDS))
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
    }
}
