package com.example.yemenport.ui.auth

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.yemenport.R
import com.example.yemenport.ui.theme.SovereignEmerald
import com.example.yemenport.ui.theme.SovereignGold
import com.example.yemenport.ui.theme.SovereignNavy
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "AuthScreen"

@Composable
fun AuthScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun handleGoogleIdCredential(idToken: String) {
        val firebaseCred = GoogleAuthProvider.getCredential(idToken, null)
        Firebase.auth.signInWithCredential(firebaseCred).await()
    }

    // Separate silent Google ID sign-in attempt on launch (single option in GetCredentialRequest)
    LaunchedEffect(activity) {
        if (activity == null || Firebase.auth.currentUser != null) return@LaunchedEffect
        try {
            val webClientId = context.getString(R.string.default_web_client_id)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(activity, request)
            val cred = result.credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
                handleGoogleIdCredential(googleCred.idToken)
            }
        } catch (_: NoCredentialException) {
            // Silent check found no previously authorized account; await interactive tap.
        } catch (_: GetCredentialCancellationException) {
            // Silent check cancelled; await interactive tap.
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Silent sign-in skipped", e)
        }
    }

    fun launchInteractiveGoogleSignIn() {
        if (activity == null) {
            errorMessage = "يتطلب تسجيل الدخول نشاطاً نشطاً."
            return
        }
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val webClientId = context.getString(R.string.default_web_client_id)
                val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOption)
                    .build()
                val result = credentialManager.getCredential(activity, request)
                val cred = result.credential
                if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
                    handleGoogleIdCredential(googleCred.idToken)
                } else {
                    errorMessage = context.getString(R.string.sign_in_failed)
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Credential Manager sign-in cancelled (check SHA-1/SHA-256 registration if unexpected)", e)
                errorMessage = context.getString(R.string.sign_in_cancelled)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Interactive Google Sign-In failed", e)
                errorMessage = e.localizedMessage ?: context.getString(R.string.sign_in_failed)
            } finally {
                isLoading = false
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_fg_img),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.republic_header),
                style = MaterialTheme.typography.labelLarge,
                color = SovereignGold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.auth_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 480.dp)
            )

            Spacer(Modifier.height(24.dp))

            ElevatedCard(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = SovereignEmerald
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.auth_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Text(
                        text = "• تخزين محلي عالي السعة (Room SQLite) مع تشفير Android Keystore\n• مسح مباشر للجوازات (CameraX + ML Kit + MRZ TD3)\n• مطابقة فورية مع قوائم المطلوبين والمضبوطات\n• مزامنة مشفرة مع السحابة وخدمات Gemini الذكية",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (errorMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Button(
                        onClick = { launchInteractiveGoogleSignIn() },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("جاري التحقق من الهوية...")
                        } else {
                            Icon(Icons.Default.Security, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.sign_in_with_google))
                        }
                    }
                }
            }
        }
    }
}
