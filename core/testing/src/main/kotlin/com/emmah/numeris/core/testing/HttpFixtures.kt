package com.emmah.numeris.core.testing

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

fun fixtureResponseBuilder(
    request: Request,
    code: Int,
    body: String,
): Response.Builder =
    Response
        .Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("Fixture")
        .body(body.toResponseBody())
