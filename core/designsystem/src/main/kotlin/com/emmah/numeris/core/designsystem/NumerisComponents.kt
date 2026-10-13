@file:Suppress("ktlint:standard:function-naming")

package com.emmah.numeris.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.emmah.numeris.core.model.CompactFormat
import java.time.Duration
import java.time.Instant

/** Päänäkymän otsikko: iso otsikko, valinnainen tilarivi (esim. [FreshnessStatus]) ja päivityspainike. */
@Composable
@Suppress("FunctionNaming")
fun NumerisScreenHeader(
    title: String,
    syncing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    status: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xs),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            status?.invoke()
        }
        RefreshButton(syncing, onRefresh)
    }
}

/**
 * Datan tuoreus otsikon alla: tilapiste ja hillitty teksti. Piste on vihreä alle vuorokauden ikäiselle
 * datalle, muuten harmaa. [source] nimeää palveluntarjoajan, kun näkymässä voi valita niistä yhden.
 */
@Composable
@Suppress("FunctionNaming")
fun FreshnessStatus(
    freshness: String?,
    modifier: Modifier = Modifier,
    source: String? = null,
) {
    val updated = freshness?.let { runCatching { Instant.parse(it) }.getOrNull() }
    val fresh = updated != null && Duration.between(updated, Instant.now()) < FRESH_FOR
    val prefix = if (source == null) "Updated" else "$source updated"
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(if (fresh) NumerisColors.Positive else NumerisColors.TertiaryText)
        Text(
            if (freshness == null) "Not synced yet" else "$prefix ${CompactFormat.relative(freshness)}",
            color = NumerisColors.TertiaryText,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private val FRESH_FOR: Duration = Duration.ofDays(1)

/** Valintasiru, joka avaa vaihtoehdot valikkoon. Valittu arvo näkyy sirussa ilman etuliitettä. */
@Composable
@Suppress("FunctionNaming")
fun NumerisDropdownSelector(
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    label: (String) -> String,
    modifier: Modifier = Modifier,
    detail: ((String) -> String)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        NumerisSelectChip(label(selected), { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = NumerisColors.Control,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xxs)) {
                            Text(
                                label(option),
                                color = if (option == selected) NumerisColors.Accent else NumerisColors.PrimaryText,
                            )
                            detail?.let {
                                Text(
                                    it(option),
                                    color = NumerisColors.TertiaryText,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}
