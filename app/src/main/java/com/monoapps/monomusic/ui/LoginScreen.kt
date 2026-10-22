package com.monoapps.monomusic.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monoapps.monomusic.AuthState
import com.monoapps.monomusic.MonoMusicViewModel
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import com.mudita.mmd.components.text.TextMMD

@Composable
fun LoginScreen(viewModel: MonoMusicViewModel, onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    val state = viewModel.authState

    LaunchedEffect(state) {
        if (state is AuthState.LoggedIn) onLoggedIn()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        TextMMD(text = "MonoMusic", fontSize = 32.sp, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(12.dp))
        TextMMD(
            text = "Streams music from Tidal. A Tidal subscription is required.",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(40.dp))

        when (state) {
            is AuthState.AwaitingApproval -> {
                TextMMD(
                    text = "On any device, go to",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )
                TextMMD(
                    text = "link.tidal.com",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextMMD(text = "and enter this code:", fontSize = 16.sp)
                Spacer(modifier = Modifier.height(16.dp))
                TextMMD(
                    text = state.userCode,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp,
                )
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicatorMMD()
                Spacer(modifier = Modifier.height(8.dp))
                TextMMD(text = "Waiting for approval…", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButtonMMD(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.url)))
                    } catch (_: ActivityNotFoundException) {
                        // No browser on this device; the code works from any other device.
                    }
                }) {
                    TextMMD(text = "Open on this phone")
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButtonMMD(onClick = { viewModel.cancelLogin() }) {
                    TextMMD(text = "Cancel")
                }
            }

            else -> {
                OutlinedButtonMMD(onClick = { viewModel.startLogin() }) {
                    TextMMD(text = "Log in with Tidal", fontSize = 18.sp)
                }
                viewModel.loginError?.let { error ->
                    Spacer(modifier = Modifier.height(16.dp))
                    TextMMD(text = error, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
