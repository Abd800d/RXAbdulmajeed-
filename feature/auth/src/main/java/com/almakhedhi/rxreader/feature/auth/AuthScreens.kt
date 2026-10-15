package com.almakhedhi.rxreader.feature.auth

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.almakhedhi.rxreader.core.designsystem.components.GradientButton
import com.almakhedhi.rxreader.core.designsystem.theme.CardOutline
import com.almakhedhi.rxreader.core.designsystem.theme.ConfidenceHigh
import com.almakhedhi.rxreader.core.designsystem.theme.ConfidenceLow
import com.almakhedhi.rxreader.core.designsystem.theme.InkMuted
import com.almakhedhi.rxreader.core.designsystem.theme.InkPrimary
import com.almakhedhi.rxreader.core.designsystem.theme.InkSecondary
import com.almakhedhi.rxreader.core.designsystem.theme.RxReaderTheme
import com.almakhedhi.rxreader.core.designsystem.theme.TealDark
import com.almakhedhi.rxreader.core.designsystem.theme.TealGradientEnd
import com.almakhedhi.rxreader.core.designsystem.theme.TealGradientStart
import com.almakhedhi.rxreader.core.designsystem.theme.TealPrimary

private enum class AuthMode {
    Login, Register, Forgot,
}

/** Login / register / password-reset flow. Shown while signed out. */
@Composable
fun AuthRoute(onContactClick: () -> Unit) {
    val viewModel: AuthViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    var mode by rememberSaveable { mutableStateOf(AuthMode.Login) }
    val switch = { next: AuthMode ->
        viewModel.clearFeedback()
        mode = next
    }

    when (mode) {
        AuthMode.Login -> LoginScreen(
            state = state,
            onSignIn = viewModel::signIn,
            onGoogle = viewModel::signInWithGoogle,
            onRegisterClick = { switch(AuthMode.Register) },
            onForgotClick = { switch(AuthMode.Forgot) },
            onContactClick = onContactClick,
        )

        AuthMode.Register -> RegisterScreen(
            state = state,
            onSignUp = viewModel::signUp,
            onLoginClick = { switch(AuthMode.Login) },
        )

        AuthMode.Forgot -> ForgotScreen(
            state = state,
            onSend = viewModel::sendReset,
            onBackClick = { switch(AuthMode.Login) },
        )
    }
}

@Composable
private fun LoginScreen(
    state: AuthUiState,
    onSignIn: (String, String) -> Unit,
    onGoogle: (Activity) -> Unit,
    onRegisterClick: () -> Unit,
    onForgotClick: () -> Unit,
    onContactClick: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val activity = LocalContext.current as Activity

    AuthScaffold(
        title = stringResource(R.string.auth_title_login),
        subtitle = stringResource(R.string.auth_sub_login),
    ) {
        AuthTextField(
            value = email,
            onValueChange = { email = it },
            labelRes = R.string.auth_email,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        )
        PasswordField(
            value = password,
            onValueChange = { password = it },
            labelRes = R.string.auth_password,
            imeAction = ImeAction.Done,
            onDone = { onSignIn(email, password) },
        )
        state.error?.let { FeedbackBox(text = it, success = false) }
        GradientButton(
            text = stringResource(R.string.auth_login_btn),
            icon = Icons.Filled.Login,
            onClick = { onSignIn(email, password) },
            loading = state.loading,
            modifier = Modifier.fillMaxWidth(),
        )
        OrDivider()
        GoogleButton(
            loading = state.googleLoading,
            onClick = { onGoogle(activity) },
        )
        RowLink(
            question = stringResource(R.string.auth_no_account),
            link = stringResource(R.string.auth_create_link),
            onClick = onRegisterClick,
        )
        TextLink(
            text = stringResource(R.string.auth_forgot_link),
            onClick = onForgotClick,
        )
        TextLink(
            text = stringResource(R.string.auth_contact),
            onClick = onContactClick,
        )
    }
}

@Composable
private fun RegisterScreen(
    state: AuthUiState,
    onSignUp: (String, String, String, String) -> Unit,
    onLoginClick: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    AuthScaffold(
        title = stringResource(R.string.auth_title_register),
        subtitle = stringResource(R.string.auth_sub_register),
    ) {
        AuthTextField(
            value = name,
            onValueChange = { name = it },
            labelRes = R.string.auth_name,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
        )
        AuthTextField(
            value = email,
            onValueChange = { email = it },
            labelRes = R.string.auth_email,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        )
        PasswordField(
            value = password,
            onValueChange = { password = it },
            labelRes = R.string.auth_password,
            imeAction = ImeAction.Next,
        )
        PasswordField(
            value = confirm,
            onValueChange = { confirm = it },
            labelRes = R.string.auth_confirm,
            imeAction = ImeAction.Done,
            onDone = { onSignUp(name, email, password, confirm) },
        )
        state.error?.let { FeedbackBox(text = it, success = false) }
        GradientButton(
            text = stringResource(R.string.auth_register_btn),
            icon = Icons.Filled.PersonAdd,
            onClick = { onSignUp(name, email, password, confirm) },
            loading = state.loading,
            modifier = Modifier.fillMaxWidth(),
        )
        RowLink(
            question = stringResource(R.string.auth_have_account),
            link = stringResource(R.string.auth_login_link),
            onClick = onLoginClick,
        )
    }
}

@Composable
private fun ForgotScreen(
    state: AuthUiState,
    onSend: (String) -> Unit,
    onBackClick: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }

    AuthScaffold(
        title = stringResource(R.string.auth_title_forgot),
        subtitle = stringResource(R.string.auth_sub_forgot),
    ) {
        AuthTextField(
            value = email,
            onValueChange = { email = it },
            labelRes = R.string.auth_email,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Send,
            onDone = { onSend(email) },
        )
        state.error?.let { FeedbackBox(text = it, success = false) }
        if (state.resetSent) {
            FeedbackBox(text = stringResource(R.string.auth_reset_sent), success = true)
        }
        GradientButton(
            text = stringResource(R.string.auth_send_btn),
            icon = Icons.Filled.Send,
            onClick = { onSend(email) },
            loading = state.loading,
            modifier = Modifier.fillMaxWidth(),
        )
        TextLink(
            text = stringResource(R.string.auth_back_login),
            onClick = onBackClick,
        )
    }
}

@Composable
private fun AuthScaffold(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    Brush.linearGradient(listOf(TealGradientStart, TealGradientEnd)),
                    shape = RoundedCornerShape(20.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Rx",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = InkPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = InkSecondary,
            textAlign = TextAlign.Center,
        )
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onDone: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(labelRes)) },
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onDone?.invoke() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TealPrimary,
            unfocusedBorderColor = CardOutline,
        ),
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    imeAction: ImeAction,
    onDone: (() -> Unit)? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(labelRes)) },
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(
                        if (visible) R.string.cd_hide else R.string.cd_show,
                    ),
                    tint = InkMuted,
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onDone?.invoke() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TealPrimary,
            unfocusedBorderColor = CardOutline,
        ),
    )
}

@Composable
private fun FeedbackBox(text: String, success: Boolean) {
    val color = if (success) ConfidenceHigh else ConfidenceLow
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = TealDark.takeIf { success } ?: color,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(CardOutline),
        )
        Text(
            text = stringResource(R.string.auth_or),
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(CardOutline),
        )
    }
}

@Composable
private fun GoogleButton(loading: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(18.dp),
        border = CardOutline.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = InkPrimary,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = TealPrimary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = null,
                tint = TealPrimary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.auth_google_btn),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun RowLink(question: String, link: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = question,
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
        )
        TextButton(onClick = onClick) {
            Text(
                text = link,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = TealPrimary,
            )
        }
    }
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onClick) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TealDark,
            )
        }
    }
}

@Preview(name = "Login - Arabic", showBackground = true, backgroundColor = 0xFFF2FAF9, locale = "ar")
@Preview(name = "Login - English", showBackground = true, backgroundColor = 0xFFF2FAF9, locale = "en")
@Composable
private fun LoginScreenPreview() {
    RxReaderTheme {
        LoginScreen(
            state = AuthUiState(),
            onSignIn = { _, _ -> },
            onGoogle = {},
            onRegisterClick = {},
            onForgotClick = {},
            onContactClick = {},
        )
    }
}
