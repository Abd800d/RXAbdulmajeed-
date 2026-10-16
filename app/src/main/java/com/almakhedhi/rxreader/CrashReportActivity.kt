package com.almakhedhi.rxreader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Last-resort crash screen: no Hilt, no nav, default theme, hardcoded
 * strings — zero dependencies on app code that might itself be broken.
 * Started by the global crash handler in [RxReaderApplication].
 */
class CrashReportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra(EXTRA_TRACE).orEmpty()
        setContent {
            MaterialTheme {
                CrashContent(trace = trace, onCopy = { copyTrace(trace) }, onRetry = { restart() })
            }
        }
    }

    private fun copyTrace(trace: String) {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("crash", trace.ifBlank { "empty" }))
        Toast.makeText(this, "تم النسخ / Copied", Toast.LENGTH_SHORT).show()
    }

    private fun restart() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
    }

    companion object {
        const val EXTRA_TRACE = "trace"
    }
}

@Composable
private fun CrashContent(trace: String, onCopy: () -> Unit, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "عذرًا — توقف التطبيق بشكل غير متوقع",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "Sorry — the app crashed unexpectedly.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            trace.ifBlank { "no details" }.take(4000),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
            Text("نسخ التقرير / Copy report")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text("إعادة فتح التطبيق / Retry")
        }
    }
}
