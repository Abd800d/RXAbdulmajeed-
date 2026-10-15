package com.almakhedhi.rxreader.feature.support

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.almakhedhi.rxreader.core.designsystem.theme.InkMuted
import com.almakhedhi.rxreader.core.designsystem.theme.InkPrimary
import com.almakhedhi.rxreader.core.designsystem.theme.InkSecondary
import com.almakhedhi.rxreader.core.designsystem.theme.RxReaderTheme
import com.almakhedhi.rxreader.core.designsystem.theme.TealContainer
import com.almakhedhi.rxreader.core.designsystem.theme.TealDark
import com.almakhedhi.rxreader.core.designsystem.theme.TealPrimary

/** "Contact us" screen: WhatsApp and email support channels. */
@Composable
fun ContactScreen(appVersion: String, onBack: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = TealPrimary,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.support_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = InkPrimary,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(TealContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = TealDark,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.support_doctor),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = InkPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.support_role),
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary,
            )
        }

        ContactCard(
            icon = Icons.Filled.Chat,
            label = stringResource(R.string.support_whatsapp),
            value = stringResource(R.string.support_whatsapp_number),
            onClick = { openWhatsApp(context, appVersion) },
        )
        ContactCard(
            icon = Icons.Filled.Email,
            label = stringResource(R.string.support_email),
            value = stringResource(R.string.support_email_address),
            onClick = { openEmail(context, appVersion) },
        )

        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.support_version, appVersion),
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ContactCard(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(TealContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = TealDark,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                    maxLines = 1,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = InkMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun openWhatsApp(context: Context, appVersion: String) {
    val text = Uri.encode(context.getString(R.string.support_wa_text, appVersion))
    val uri = Uri.parse("https://wa.me/967781733795?text=$text")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp"))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}

private fun openEmail(context: Context, appVersion: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:7a8b1d@gmail.com")).apply {
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.support_email_subject))
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.support_email_body, appVersion))
    }
    context.startActivity(Intent.createChooser(intent, null))
}

@Preview(name = "Contact - Arabic", showBackground = true, backgroundColor = 0xFFF2FAF9, locale = "ar")
@Preview(name = "Contact - English", showBackground = true, backgroundColor = 0xFFF2FAF9, locale = "en")
@Composable
private fun ContactScreenPreview() {
    RxReaderTheme {
        ContactScreen(appVersion = "0.1.0", onBack = {})
    }
}
