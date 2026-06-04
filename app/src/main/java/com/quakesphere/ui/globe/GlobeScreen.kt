package com.quakesphere.ui.globe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.quakesphere.domain.model.DepthCategory
import com.quakesphere.domain.model.Earthquake
import com.quakesphere.ui.theme.DepthDeep
import com.quakesphere.ui.theme.DepthIntermediate
import com.quakesphere.ui.theme.DepthShallow
import com.quakesphere.ui.theme.ElectricBlue
import com.quakesphere.ui.theme.MagGreat
import com.quakesphere.ui.theme.MagMajor
import com.quakesphere.ui.theme.MagMinor
import com.quakesphere.ui.theme.MagModerate
import com.quakesphere.ui.theme.MagStrong
import com.quakesphere.ui.theme.SurfaceCard
import com.quakesphere.ui.theme.TextPrimary
import com.quakesphere.ui.theme.TextSecondary
import androidx.compose.material3.ExperimentalMaterial3Api
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobeScreen(
    onNavigateToList: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: GlobeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Full-screen Globe
        AndroidView(
            factory = { context ->
                GlobeView(context).apply {
                    onEarthquakeTapped = { index ->
                        viewModel.selectEarthquake(index)
                    }
                }
            },
            update = { view ->
                view.renderer.updateEarthquakes(uiState.earthquakes)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top bar overlay
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title
                Column {
                    Text(
                        text = "QuakeSphere",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${uiState.earthquakes.size} quakes tracked",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = ElectricBlue,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(onClick = { viewModel.syncEarthquakes() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ElectricBlue)
                    }
                    BadgedBox(badge = {
                        if (uiState.earthquakes.isNotEmpty()) {
                            Badge(containerColor = MagStrong) {
                                Text(
                                    text = uiState.earthquakes.size.toString(),
                                    color = Color.White,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }) {
                        IconButton(onClick = onNavigateToList) {
                            Icon(Icons.Default.List, contentDescription = "List", tint = TextPrimary)
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextPrimary)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Magnitude legend
            MagnitudeLegend(
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(start = 12.dp, bottom = if (uiState.selectedEarthquake != null) 220.dp else 16.dp)
            )
        }

        // Bottom sheet for selected earthquake
        AnimatedVisibility(
            visible = uiState.selectedEarthquake != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            uiState.selectedEarthquake?.let { quake ->
                SelectedEarthquakeCard(
                    earthquake = quake,
                    onViewDetails = { onNavigateToDetail(quake.id) },
                    onDismiss = { viewModel.clearSelection() },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun MagnitudeLegend(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "DEPTH",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            LegendItem(color = DepthShallow, label = "Shallow <70km")
            LegendItem(color = DepthIntermediate, label = "Mid 70-300km")
            LegendItem(color = DepthDeep, label = "Deep >300km")
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(text = label, color = TextPrimary, fontSize = 11.sp)
    }
}

@Composable
fun SelectedEarthquakeCard(
    earthquake: Earthquake,
    onViewDetails: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MagnitudeBadgeLarge(magnitude = earthquake.mag)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = earthquake.place,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = formatTimeAgo(earthquake.time),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                Text(
                    text = "✕",
                    color = TextSecondary,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoChip(
                    label = "Depth",
                    value = "${earthquake.depth.toInt()} km",
                    color = when (earthquake.depthCategory) {
                        DepthCategory.SHALLOW -> DepthShallow
                        DepthCategory.INTERMEDIATE -> DepthIntermediate
                        DepthCategory.DEEP -> DepthDeep
                    }
                )
                InfoChip(
                    label = "Coordinates",
                    value = "${String.format("%.2f", earthquake.lat)}°, ${String.format("%.2f", earthquake.lon)}°",
                    color = ElectricBlue
                )
                if (earthquake.tsunami == 1) {
                    InfoChip(label = "Tsunami", value = "WARNING", color = MagGreat)
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onViewDetails,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
            ) {
                Text("View Full Details", color = Color.White)
            }
        }
    }
}

@Composable
fun MagnitudeBadgeLarge(magnitude: Double) {
    val color = magnitudeColor(magnitude)
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = String.format("%.1f", magnitude),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@Composable
fun InfoChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = TextSecondary, fontSize = 10.sp)
        Text(text = value, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

fun magnitudeColor(mag: Double): Color {
    return when {
        mag < 5.0 -> MagMinor
        mag < 6.0 -> MagModerate
        mag < 7.0 -> MagStrong
        mag < 8.0 -> MagMajor
        else -> MagGreat
    }
}

fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> {
            val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
