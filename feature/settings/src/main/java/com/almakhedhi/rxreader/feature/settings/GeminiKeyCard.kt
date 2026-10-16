package com.almakhedhi.rxreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.almakhedhi.rxreader.core.designsystem.theme.InkMuted
import com.almakhedhi.rxreader.core.designsystem.theme.TealPrimary

/**
 * Runtime Gemini key entry: paste a free AI Studio key to enable AI without
 * a Firebase project or a rebuild. Stored app-private on this device only.
 */
@Composable
fun GeminiKeyCard(currentKey: String, onSave: (String) -> Unit) {
    var draft by remember(currentKey) { mutableStateOf(currentKey) }
    var visible by remember { mutableStateOf(false) }
    val unchanged = draft.trim() == currentKey.trim()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.key_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.key_desc),
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.key_label)) },
                singleLine = true,
                visualTransformation = if (visible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null,
                        )
                    }
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onSave(draft.trim()) },
                    enabled = !unchanged,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.key_save))
                }
                TextButton(
                    onClick = { onSave("") },
                    enabled = currentKey.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.key_clear))
                }
            }
            Text(
                if (currentKey.isNotBlank()) {
                    stringResource(R.string.key_active)
                } else {
                    stringResource(R.string.key_missing)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (currentKey.isNotBlank()) TealPrimary else InkMuted,
            )
        }
    }
}
