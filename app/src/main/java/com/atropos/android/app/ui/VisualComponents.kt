/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.Image
import androidx.compose.material3.icons.filled.PhotoSizeSelectActual
import androidx.compose.material3.icons.filled.RemoveRedEye
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.launch

/**
 * Visual Components for Android (F-VIS-008, F-VIS-009, F-VIS-010).
 *
 * F-VIS-008: Android open frame - full-screen image viewer for screenshots
 * F-VIS-009: Android hero stream - live visual feed from engine
 * F-VIS-010: Android footer composer - integrated visual input
 */

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VisualOpenFrame(
    bitmap: ImageBitmap?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Dark overlay background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .fillMaxSize(),
        )

        // Image or placeholder
        bitmap?.let { bmp ->
            androidx.compose.foundation.Image(
                bitmap = bmp,
                contentDescription = "Visual frame",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(16.dp)
                    .sizeIn(
                        maxWidth = 0.95f * androidx.compose.ui.unit.Constraints.mustHaveConstraints(),
                        maxHeight = 0.9f * androidx.compose.ui.unit.Constraints.mustHaveConstraints(),
                    )
                    .fillMaxSize(0.95f),
            )
        } ?: Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "No visual frame available",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Close button
        androidx.compose.material3.IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(48.dp),
            onClick = onClose,
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close visual frame",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * F-VIS-009: Android Hero Stream
 *
 * A live visual feed component that displays screenshots or visual
 * frames pushed from the engine via `/v1/visual/compare` or similar
 * visual stream endpoints.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HeroStream(
    onFrameRequest: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentFrame = remember { mutableStateOf<ImageBitmap?>(null) }
    val loading = remember { mutableStateOf(false) }
    val error = remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Request initial frame
    LaunchedEffect(Unit) {
        onFrameRequest?.invoke()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            currentFrame.value?.let { bitmap ->
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = "Hero stream frame",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } ?: Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                loading.value?.let {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.padding(bottom = 16.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                error.value?.let { err ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Hero stream unavailable", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                (!loading.value && !error.value && currentFrame.value == null)?.let {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.PhotoSizeSelectActual,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(64.dp).padding(bottom = 16.dp)
                        )
                        Text(
                            text = "No visual frame — request one from the engine",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Refresh button overlay
        androidx.compose.material3.IconButton(
            onClick = { onFrameRequest?.invoke() },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            enabled = !loading.value,
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh frame",
            )
        }

        // Error dismiss
        error.value?.let {
            androidx.compose.material3.Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = { error.value = null },
                visual = {
                    Text(error.value!!, style = MaterialTheme.typography.bodySmall)
                },
                dismissAction = { error.value = null },
            )
        }
    }
}

/**
 * F-VIS-010: Android Footer Composer
 *
 * A composer that integrates visual input (screenshots, camera)
 * alongside text input. The composer sits in the footer and
 * supports both text and visual input modes.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FooterComposer(
    onTextSend: (String) -> Unit,
    onVisualSend: (ImageBitmap) -> Unit,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    val textFieldValue = remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(text)) }
    var visualMode by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        // Visual mode indicator
        if (visualMode) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.RemoveRedEye,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                    Text("Visual Mode", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                androidx.compose.material3.IconButton(
                    onClick = { visualMode = false },
                    contentDescription = "Exit visual mode",
                ) {
                    androidx.compose.material3.Icon(Icons.Default.Close, contentDescription = null)
                }
            }
        }

        // Main composer row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Visual input button
            androidx.compose.material3.IconButton(
                onClick = { visualMode = true },
                modifier = Modifier.size(44.dp),
                contentDescription = "Add visual input",
            ) {
                androidx.compose.material3.Icon(Icons.Default.Image, contentDescription = null)
            }

            // Text field
            androidx.compose.material3.TextField(
                value = text,
                onValueChange = { newText -> text = newText },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                placeholder = { Text("Type or add visual…", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.foundation.text.KeyboardType.Text,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Send,
                    capitalization = androidx.compose.foundation.text.KeyboardCapitalization.Sentences,
                ),
                keyboardActions = androidx.compose.material3.TextFieldKeyboardActions(
                    onDone = { if (text.isNotBlank()) onTextSend(text) },
                ),
            )

            // Send button
            androidx.compose.material3.Button(
                onClick = { if (text.isNotBlank()) onTextSend(text) },
                modifier = Modifier.size(44.dp).padding(start = 4.dp),
                enabled = text.isNotBlank(),
            ) {
                androidx.compose.material3.Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}