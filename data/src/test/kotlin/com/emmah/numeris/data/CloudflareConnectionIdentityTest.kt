package com.emmah.numeris.data

import com.emmah.numeris.core.database.ConnectionEntity
import com.emmah.numeris.core.model.ConnectionId
import com.emmah.numeris.core.model.SaveConnectionCommand
import com.emmah.numeris.core.model.SourceId
import org.junit.Assert.assertThrows
import org.junit.Test

class CloudflareConnectionIdentityTest {
    private val existing =
        ConnectionEntity(
            "cloudflare:example.com",
            "cloudflare",
            config = """{"domain":"example.com","zoneId":"old-zone"}""",
        )

    @Test fun sameDomainCanUpdateZoneAndNormalizedSpelling() {
        command(existing.id, "https://EXAMPLE.com/").validateCloudflareDomain(listOf(existing))
    }

    @Test fun editingCannotRetargetAnExistingIdentity() {
        assertThrows(IllegalArgumentException::class.java) {
            command(existing.id, "other.example").validateCloudflareDomain(listOf(existing))
        }
    }

    @Test fun anotherIdentityCannotClaimAnExistingDomain() {
        assertThrows(IllegalArgumentException::class.java) {
            command("cloudflare:another-id", "https://EXAMPLE.com/").validateCloudflareDomain(listOf(existing))
        }
    }

    @Test fun newDomainCanBeAdded() {
        command("cloudflare:other.example", "other.example").validateCloudflareDomain(listOf(existing))
    }

    private fun command(
        id: String,
        domain: String,
    ) = SaveConnectionCommand(
        ConnectionId(id),
        SourceId.CloudflareZone,
        domain,
        mapOf("domain" to domain, "zoneId" to "replacement-zone"),
        emptyMap(),
    )
}
