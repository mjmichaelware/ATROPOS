/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atropos.android.app.bridge.MobileApproval

/**
 * HOE-D07: the card that stops the engine and asks a person.
 *
 * It takes the whole [MobileApproval] rather than an id. The previous version
 * rendered "Approval Required" and "ID: apr-8f2c" over an Approve button —
 * which asks someone to authorise an action whose operation, actor and
 * territory they cannot see. An approval granted without knowing what is being
 * approved is not an approval; it is a button press, and it is worse than no
 * gate because it produces a signed record saying a person agreed.
 *
 * Territory is stated explicitly, including when it is empty. The engine's
 * projection is careful that an empty territory means "declared none", never
 * "all paths", and this card keeps that distinction visible: an action that
 * named no bounds is the one most worth reading twice.
 *
 * Reject is the low-friction side. Approve carries the weight, so it is the
 * filled button and sits where a deliberate press lands, while Reject stays a
 * text button — a person who is unsure should find refusing easier than
 * agreeing.
 */
@Composable
fun MobileApprovalCard(
    approval: MobileApproval,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Approval required", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(12.dp))

            DetailRow("Operation", approval.operation.ifBlank { "unnamed operation" })
            DetailRow("Requested by", approval.actor.ifBlank { "unattributed" })
            DetailRow("Territory", territoryLabel(approval.territory))
            if (approval.reason.isNotBlank()) DetailRow("Reason", approval.reason)
            DetailRow("Requested", approval.requestedAt.ifBlank { "unrecorded" })

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onReject(approval.id) }) { Text("Reject") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onApprove(approval.id) }) { Text("Approve") }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(108.dp)
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * How an empty territory reads.
 *
 * "no territory declared" rather than a blank or a dash. A blank field invites
 * the reader to fill it in themselves, and the two things they might fill in —
 * "nothing" and "everything" — are the two opposite readings this must not
 * leave open.
 */
internal fun territoryLabel(territory: List<String>): String =
    if (territory.isEmpty()) "no territory declared" else territory.joinToString(", ")
