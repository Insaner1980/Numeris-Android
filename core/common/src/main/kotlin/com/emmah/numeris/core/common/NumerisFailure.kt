package com.emmah.numeris.core.common

sealed class NumerisFailure(
    message: String,
) : Exception(message) {
    class Authentication(
        message: String,
    ) : NumerisFailure(message)

    class Authorization(
        message: String,
    ) : NumerisFailure(message)

    class RateLimit(
        message: String,
    ) : NumerisFailure(message)

    class TransientNetwork(
        message: String,
    ) : NumerisFailure(message)

    class Provider(
        message: String,
    ) : NumerisFailure(message)

    class InvalidConfiguration(
        message: String,
    ) : NumerisFailure(message)

    class Storage(
        message: String,
    ) : NumerisFailure(message)

    class Import(
        message: String,
    ) : NumerisFailure(message)
}
