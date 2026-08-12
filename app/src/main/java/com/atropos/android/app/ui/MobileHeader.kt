/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The status bar: who you are talking to, and whether it can answer.
 *
 * Engine reachability is stated in words as well as colour. A conversation
 * client that silently stops answering is indistinguishable from one that is
 * thinking, and the operator has no way to tell which without being told.
 */
@Composable
fun MobileHeader(isOnline: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ATROPOS",
                style = MaterialTheme.typography.titleMedium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(10.dp),
                    shape = MaterialTheme.shapes.small,
                    color = if (isOnline) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                ) {}
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isOnline) "engine online" else "engine offline",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
