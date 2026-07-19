package com.vctrch.golfgps.feature.contribute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vctrch.golfgps.data.opengolf.OpenGolfAuthStore
import com.vctrch.golfgps.ui.theme.GolfTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenGolfSignInSheet(
    authStore: OpenGolfAuthStore,
    onDismiss: () -> Unit,
) {
    val auth by authStore.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var modeCreate by remember { mutableStateOf(true) }
    var emailField by remember { mutableStateOf(auth.email) }
    var codeField by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (modeCreate) "Create OpenGolf account" else "Sign in to OpenGolf",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (modeCreate) {
                    "Enter your email for a free OpenGolf account. You’ll get a one-time code — no password. " +
                        "That account lets Community Golf submit tee, green, and pin updates."
                } else {
                    "Already have an OpenGolf account? Enter the same email and we’ll send a sign-in code."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { modeCreate = true }) {
                    Text("Create account", color = if (modeCreate) GolfTheme.Fairway else MaterialTheme.colorScheme.onSurface)
                }
                TextButton(onClick = { modeCreate = false }) {
                    Text("Sign in", color = if (!modeCreate) GolfTheme.Fairway else MaterialTheme.colorScheme.onSurface)
                }
            }

            when (auth.phase) {
                OpenGolfAuthStore.Phase.SIGNED_OUT,
                OpenGolfAuthStore.Phase.SIGNED_IN,
                -> {
                    if (auth.phase == OpenGolfAuthStore.Phase.SIGNED_IN) {
                        Text(
                            "Signed in as ${auth.email}",
                            color = GolfTheme.Fairway,
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(
                            onClick = {
                                authStore.signOut()
                                codeField = ""
                            },
                        ) {
                            Text("Sign out")
                        }
                    } else {
                        OutlinedTextField(
                            value = emailField,
                            onValueChange = { emailField = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Email") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    authStore.requestSignInCode(
                                        emailField,
                                        if (modeCreate) {
                                            OpenGolfAuthStore.AuthIntent.CREATE_ACCOUNT
                                        } else {
                                            OpenGolfAuthStore.AuthIntent.SIGN_IN
                                        },
                                    )
                                }
                            },
                            enabled = emailField.isNotBlank() && !auth.isBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (modeCreate) "Create account" else "Email me a code")
                        }
                    }
                }
                OpenGolfAuthStore.Phase.AWAITING_CODE -> {
                    Text(
                        auth.statusMessage ?: "Enter the code from your email.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = codeField,
                        onValueChange = { codeField = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Button(
                        onClick = { scope.launch { authStore.verifyCode(codeField) } },
                        enabled = codeField.isNotBlank() && !auth.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (modeCreate) "Verify and create account" else "Verify and sign in")
                    }
                    TextButton(
                        onClick = {
                            codeField = ""
                            authStore.signOut()
                        },
                    ) {
                        Text("Use a different email")
                    }
                }
            }

            if (auth.isBusy) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
            }
            auth.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            auth.statusMessage?.takeIf { auth.phase == OpenGolfAuthStore.Phase.SIGNED_IN }?.let {
                Text(it, color = GolfTheme.Fairway)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
    }
}
