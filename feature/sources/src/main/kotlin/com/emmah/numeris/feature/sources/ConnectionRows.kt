@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.feature.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.emmah.numeris.core.designsystem.ListCard
import com.emmah.numeris.core.designsystem.ListRow
import com.emmah.numeris.core.designsystem.NumerisColors
import com.emmah.numeris.core.designsystem.NumerisSizes
import com.emmah.numeris.core.designsystem.NumerisSpacing
import com.emmah.numeris.core.designsystem.PillTone
import com.emmah.numeris.core.designsystem.StatusPill
import com.emmah.numeris.core.model.CompactFormat
import com.emmah.numeris.core.model.Connection
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.core.model.displayName
import com.emmah.numeris.data.GoogleAuthorizationFeedback
import java.time.Duration
import java.time.Instant
import com.emmah.numeris.core.designsystem.R as DesignSystemR

private const val STALE_AFTER_HOURS = 48L

/** Yhteyden nimi: sivuston verkkotunnus, jos sellainen on, muuten palvelun nimi raakatunnisteen sijaan. */
internal fun connectionTitle(connection: Connection): String =
    connection.displayName.takeIf { it.contains('.') && ' ' !in it } ?: connection.source.displayName

/** Tilapillerin teksti ja sävy. Sama tulkinta näkyy otsikon yhteenvedossa. */
internal fun connectionStatus(
    connection: Connection,
    now: Instant = Instant.now(),
): Pair<String, PillTone> {
    val synced = connection.lastSuccessfulSync
    return when {
        connection.source == SourceId.GoogleAnalytics -> "Retired" to PillTone.Neutral
        !connection.configured -> "Disconnected" to PillTone.Neutral
        connection.lastError != null -> "Action needed" to PillTone.Warning
        synced == null -> "Not synced" to PillTone.Neutral
        Duration.between(synced, now).toHours() >= STALE_AFTER_HOURS -> "Stale" to PillTone.Warning
        else -> "OK" to PillTone.Positive
    }
}

/** Palvelu, synkronointitila ja mahdollinen virhe kukin omalla rivillään. */
private fun connectionSupporting(connection: Connection): String {
    val provider = connection.source.displayName.takeIf { it != connectionTitle(connection) }
    val status =
        when {
            connection.source == SourceId.GoogleAnalytics -> "Cached history and credentials are preserved"
            connection.lastSuccessfulSync == null -> "Not synced yet"
            else -> "Synced ${CompactFormat.relative(connection.lastSuccessfulSync.toString())}"
        }
    return listOfNotNull(provider, status, connection.lastError).joinToString("\n")
}

@Composable
internal fun ConnectionGroup(
    connections: List<Connection>,
    actions: ConnectionActions,
    modifier: Modifier = Modifier,
    hasFooter: Boolean = false,
    footer: @Composable () -> Unit = {},
) {
    ListCard(modifier) {
        connections.forEachIndexed { index, connection ->
            ConnectionRow(connection, actions, divider = hasFooter || index < connections.lastIndex)
        }
        footer()
    }
}

@Composable
private fun ConnectionRow(
    connection: Connection,
    actions: ConnectionActions,
    divider: Boolean,
) {
    val (label, tone) = connectionStatus(connection)
    ListRow(
        connectionTitle(connection),
        supporting = connectionSupporting(connection),
        divider = divider,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(label, tone)
                ConnectionMenu(connection, actions)
            }
        },
    )
}

@Composable
private fun ConnectionMenu(
    connection: Connection,
    actions: ConnectionActions,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        if (actions.busy) {
            Box(Modifier.size(NumerisSizes.TouchTarget), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(NumerisSizes.SmallIcon), strokeWidth = 2.dp)
            }
        } else {
            IconButton({ expanded = true }) {
                Icon(
                    painterResource(DesignSystemR.drawable.ic_numeris_more_vert),
                    contentDescription = "Actions for ${connectionTitle(connection)}",
                    tint = NumerisColors.SecondaryText,
                )
            }
        }
        DropdownMenu(expanded, { expanded = false }, containerColor = NumerisColors.Control) {
            if (connection.source != SourceId.GoogleAnalytics) {
                DropdownMenuItem({ Text("Edit") }, {
                    expanded = false
                    actions.onEdit(connection)
                })
                DropdownMenuItem({ Text("Test connection") }, {
                    expanded = false
                    actions.onTest(connection)
                })
            }
            DropdownMenuItem({ Text("Disconnect", color = NumerisColors.Negative) }, {
                expanded = false
                actions.onDelete(connection)
            })
        }
    }
}

@Composable
internal fun GoogleAuthorizationRow(
    feedback: GoogleAuthorizationFeedback?,
    authorizeGoogle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = NumerisSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xxs),
    ) {
        Text("Google account", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Read-only Search Console access. No client secret is stored.",
            color = NumerisColors.TertiaryText,
            style = MaterialTheme.typography.bodyMedium,
        )
        feedback?.let {
            Text(
                it.message,
                color = if (it.authorized) NumerisColors.Positive else NumerisColors.Warning,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        FilledTonalButton(authorizeGoogle, Modifier.padding(top = NumerisSpacing.Sm)) {
            Text(if (feedback?.authorized == true) "Authorize Google again" else "Authorize Google")
        }
    }
}

@Composable
internal fun AddSourceButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick, modifier) {
        Icon(
            painterResource(DesignSystemR.drawable.ic_numeris_add),
            contentDescription = null,
            modifier = Modifier.size(NumerisSizes.SmallIcon),
        )
        Text("Add source", Modifier.padding(start = NumerisSpacing.Xs))
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AddSourceSheet(
    onDismiss: () -> Unit,
    onChoose: (ConnectionSetup) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NumerisColors.Card,
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = NumerisSpacing.Lg),
            contentPadding = PaddingValues(bottom = NumerisSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xxs),
        ) {
            item { Text("Add source", style = MaterialTheme.typography.titleLarge) }
            items(connectionSetups, key = { it.source.name }) { provider ->
                ListRow(
                    provider.title,
                    supporting = provider.description,
                    divider = true,
                    onClick = { onChoose(provider) },
                )
            }
        }
    }
}

@Composable
internal fun ImportRow(
    enabled: Boolean,
    onChooseFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListCard(modifier) {
        ListRow(
            "Import from Windows",
            supporting =
                "Open an encrypted .nmrx export from Windows Sources. Google sign-in is completed again on Android.",
            onClick = onChooseFile.takeIf { enabled },
            leading = {
                Icon(
                    painterResource(DesignSystemR.drawable.ic_numeris_upload_file),
                    contentDescription = null,
                    tint = NumerisColors.SecondaryText,
                )
            },
            trailing = {
                Icon(
                    painterResource(DesignSystemR.drawable.ic_numeris_chevron_right),
                    contentDescription = null,
                    tint = NumerisColors.TertiaryText,
                )
            },
        )
    }
}
