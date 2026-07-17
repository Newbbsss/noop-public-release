package com.noop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Shared chevron day / night strip (Fable 200 #19) — same older/newer grammar as Today’s
 * [dayNavOlder]/[dayNavNewer] and Sleep’s night offset. Title is tappable when [onTitleClick] is set.
 */
@Composable
fun SharedDayNavStrip(
    title: String,
    subtitle: String? = null,
    canGoOlder: Boolean,
    canGoNewer: Boolean,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    onTitleClick: (() -> Unit)? = null,
    olderContentDescription: String = "Previous day",
    newerContentDescription: String = "Next day",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = onOlder,
            enabled = canGoOlder,
            modifier = Modifier.semantics {
                contentDescription = olderContentDescription
            },
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = if (canGoOlder) Palette.accent else Palette.textTertiary,
                modifier = Modifier.size(28.dp),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onTitleClick != null) {
                        Modifier.clickable(onClick = onTitleClick)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = Metrics.space8),
        ) {
            Text(
                title,
                style = NoopType.subhead,
                color = Palette.textPrimary,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = NoopType.caption,
                    color = Palette.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        IconButton(
            onClick = onNewer,
            enabled = canGoNewer,
            modifier = Modifier.semantics {
                contentDescription = newerContentDescription
            },
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (canGoNewer) Palette.accent else Palette.textTertiary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
