package com.calmapps.calmmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD

@Composable
fun PermissionsOnboardingScreen(
    hasBatteryOptimizationExemption: Boolean,
    hasStorageAccess: Boolean,
    onRequestBatteryOptimizationClick: () -> Unit,
    onRequestStorageAccessClick: () -> Unit,
    onContinueClick: () -> Unit,
    onSkipClick: () -> Unit,
) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextMMD(
                    text = "Welcome to MonoMusic",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                TextMMD(
                    text = "Before getting started, MonoMusic needs a few permissions to be most useful.\n" +
                            "\n" +
                            "Most phones now have smart battery optimizations which can prevent " +
                            "MonoMusic from continuing to run in the background when not actively playing " +
                            "a song.\n" +
                            "\n" +
                            "Downloaded songs are saved to Music/MonoMusic on your SD card or phone " +
                            "storage. Reading songs added there by other apps or a computer " +
                            "requires the audio permission.",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedButtonMMD(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRequestBatteryOptimizationClick,
                    enabled = !hasBatteryOptimizationExemption,
                    contentPadding = PaddingValues(12.dp),
                ) {
                    TextMMD(
                        text = if (hasBatteryOptimizationExemption) {
                            "Background optimization already allowed"
                        } else {
                            "Allow MonoMusic to run in background"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButtonMMD(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRequestStorageAccessClick,
                    enabled = !hasStorageAccess,
                    contentPadding = PaddingValues(12.dp),
                ) {
                    TextMMD(
                        text = if (hasStorageAccess) {
                            "Music access already allowed"
                        } else {
                            "Allow access to your music"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }

                if (hasBatteryOptimizationExemption && hasStorageAccess) {
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButtonMMD(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onContinueClick,
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        TextMMD(
                            text = "Continue",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextMMD(
                    text = "Skip for now",
                    fontSize = 18.sp,
                    modifier = Modifier
                        .padding(top = 4.dp, bottom = 4.dp)
                        .clickable(onClick = onSkipClick),
                )
            }
        }
}
