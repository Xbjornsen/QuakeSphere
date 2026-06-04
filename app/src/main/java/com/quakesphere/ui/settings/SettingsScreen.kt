package com.quakesphere.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quakesphere.ui.theme.ElectricBlue
import com.quakesphere.ui.theme.MagStrong
import com.quakesphere.ui.theme.SpaceBlack
import com.quakesphere.ui.theme.SurfaceCard
import com.quakesphere.ui.theme.SurfaceVariant
import com.quakesphere.ui.theme.TextPrimary
import com.quakesphere.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SpaceBlack)
            )
        },
        containerColor = SpaceBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Filter settings
            SettingsSectionCard(title = "Earthquake Filter") {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Minimum Magnitude", color = TextPrimary, fontSize = 15.sp)
                        Text(
                            text = String.format("M %.1f", uiState.minMagnitude),
                            color = ElectricBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Only show earthquakes above this magnitude",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Slider(
                        value = uiState.minMagnitude,
                        onValueChange = { viewModel.setMinMagnitude(it) },
                        valueRange = 4.0f..8.0f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = ElectricBlue,
                            activeTrackColor = ElectricBlue,
                            inactiveTrackColor = SurfaceVariant
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("M 4.0", color = TextSecondary, fontSize = 11.sp)
                        Text("M 8.0", color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }

            // Notification settings
            SettingsSectionCard(title = "Notifications") {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Enable Notifications", color = TextPrimary, fontSize = 15.sp)
                            Text(
                                "Get alerted when major quakes occur",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = uiState.notificationsEnabled,
                            onCheckedChange = { viewModel.setNotificationsEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ElectricBlue,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = SurfaceVariant
                            )
                        )
                    }

                    if (uiState.notificationsEnabled) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = SurfaceVariant)
                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Notification Threshold", color = TextPrimary, fontSize = 15.sp)
                            Text(
                                text = String.format("M %.1f", uiState.notificationThreshold),
                                color = MagStrong,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Notify when magnitude exceeds this value",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Slider(
                            value = uiState.notificationThreshold,
                            onValueChange = { viewModel.setNotificationThreshold(it) },
                            valueRange = 5.0f..8.0f,
                            steps = 5,
                            colors = SliderDefaults.colors(
                                thumbColor = MagStrong,
                                activeTrackColor = MagStrong,
                                inactiveTrackColor = SurfaceVariant
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("M 5.0", color = TextSecondary, fontSize = 11.sp)
                            Text("M 8.0", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Sync interval settings
            SettingsSectionCard(title = "Sync Interval") {
                Column {
                    Text(
                        text = "How often to check for new earthquakes",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SyncInterval.values().forEach { interval ->
                            FilterChip(
                                selected = uiState.syncInterval == interval,
                                onClick = { viewModel.setSyncInterval(interval) },
                                label = {
                                    Text(
                                        text = interval.label,
                                        color = if (uiState.syncInterval == interval) Color.White else TextSecondary,
                                        fontSize = 13.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue,
                                    containerColor = SurfaceVariant
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Info card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "DATA SOURCE",
                        color = ElectricBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "QuakeSphere uses live data from the USGS Earthquake Hazards Program. Data is updated in near real-time and covers global seismic events.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title.uppercase(),
                color = ElectricBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
