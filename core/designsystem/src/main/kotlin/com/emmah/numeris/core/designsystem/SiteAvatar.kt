@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/**
 * Omien sivustojen logot. Kuvat ovat sovellusten omista projekteista (dBcheck, runcheck, fonecheck, KnitTools
 * ja Finnvekin sivusto); tuntemattomalle sivustolle näytetään kirjainmerkki sivuston tunnusvärillä.
 */
@DrawableRes
fun siteLogo(domain: String): Int? =
    when (domain.lowercase().removePrefix("www.")) {
        "dbcheck.app" -> R.drawable.site_logo_dbcheck
        "runcheckapp.com" -> R.drawable.site_logo_runcheck
        "fonecheck.app" -> R.drawable.site_logo_fonecheck
        "knittoolsapp.com" -> R.drawable.site_logo_knittools
        "finnvek.com" -> R.drawable.site_logo_finnvek
        else -> null
    }

/** Sivuston merkki listariveille ja korteille: logo, tai kirjainmerkki jos logoa ei ole. */
@Composable
fun SiteAvatar(
    domain: String,
    domains: List<String>,
    modifier: Modifier = Modifier,
    size: Dp = NumerisSizes.Avatar,
) {
    val logo = siteLogo(domain)
    if (logo == null) {
        LetterAvatar(domain, modifier.size(size), color = siteColor(domain, domains))
    } else {
        Image(
            painterResource(logo),
            contentDescription = null,
            modifier = modifier.size(size).clip(RoundedCornerShape(NumerisShapes.ControlRadius)),
            contentScale = ContentScale.Crop,
        )
    }
}
