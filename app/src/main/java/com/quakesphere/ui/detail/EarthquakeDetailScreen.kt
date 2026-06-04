package com.quakesphere.ui.detail

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quakesphere.domain.model.DepthCategory
import com.quakesphere.domain.model.Earthquake
import com.quakesphere.ui.globe.formatDepth
import com.quakesphere.ui.globe.magnitudeColor
import com.quakesphere.ui.theme.DepthDeep
import com.quakesphere.ui.theme.DepthIntermediate
import com.quakesphere.ui.theme.DepthShallow
import com.quakesphere.ui.theme.ElectricBlue
import com.quakesphere.ui.theme.SpaceBlack
import com.quakesphere.ui.theme.SurfaceCard
import com.quakesphere.ui.theme.SurfaceVariant
import com.quakesphere.ui.theme.TextPrimary
import com.quakesphere.ui.theme.TextSecondary
import com.quakesphere.ui.theme.TsunamiWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarthquakeDetailScreen(
    earthquakeId: String,
    onNavigateBack: () -> Unit,
    viewModel: EarthquakeDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(earthquakeId) {
        viewModel.loadEarthquake(earthquakeId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Earthquake Details", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SpaceBlack)
            )
        },
        containerColor = SpaceBlack
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ElectricBlue)
                }
            }
            uiState.earthquake == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Earthquake not found", color = TextSecondary, fontSize = 16.sp)
                }
            }
            else -> {
                uiState.earthquake?.let { quake ->
                    EarthquakeDetailContent(
                        earthquake = quake,
                        useMiles   = uiState.useMiles,
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
    }
}

@Composable
fun EarthquakeDetailContent(
    earthquake: Earthquake,
    useMiles: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header card with magnitude
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val magColor = magnitudeColor(earthquake.mag)
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(magColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = String.format("%.1f", earthquake.mag),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = earthquake.magnitudeCategory.label.uppercase(),
                    color = magColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = earthquake.place,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatFullDate(earthquake.time),
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                if (earthquake.tsunami == 1) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .background(TsunamiWarning.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = TsunamiWarning, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("TSUNAMI WARNING", color = TsunamiWarning, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Location details card
        DetailCard(title = "Location") {
            DetailRow(label = "Region", value = earthquake.place, icon = Icons.Default.LocationOn)
            HorizontalDivider(color = SurfaceVariant, thickness = 1.dp)
            DetailRow(label = "Latitude", value = "${String.format("%.4f", earthquake.lat)}°")
            HorizontalDivider(color = SurfaceVariant, thickness = 1.dp)
            DetailRow(label = "Longitude", value = "${String.format("%.4f", earthquake.lon)}°")
        }

        // Depth card with visual
        DetailCard(title = "Depth Analysis") {
            val depthColor = when (earthquake.depthCategory) {
                DepthCategory.SHALLOW -> DepthShallow
                DepthCategory.INTERMEDIATE -> DepthIntermediate
                DepthCategory.DEEP -> DepthDeep
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Vertical depth bar visualization
                DepthBar(depth = earthquake.depth, color = depthColor)

                Column {
                    Text(
                        text = formatDepth(earthquake.depth, useMiles),
                        color = depthColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    )
                    Text(
                        text = earthquake.depthCategory.label,
                        color = depthColor,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = getTectonicHint(earthquake),
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Event details card
        DetailCard(title = "Event Details") {
            DetailRow(label = "USGS Event ID", value = earthquake.id)
            HorizontalDivider(color = SurfaceVariant, thickness = 1.dp)
            DetailRow(label = "Status", value = earthquake.status.replaceFirstChar { it.uppercase() })
            HorizontalDivider(color = SurfaceVariant, thickness = 1.dp)
            DetailRow(label = "Significance", value = earthquake.sig.toString())
            if (earthquake.alert != null) {
                HorizontalDivider(color = SurfaceVariant, thickness = 1.dp)
                DetailRow(label = "Alert Level", value = earthquake.alert.uppercase())
            }
        }

        // USGS link button
        if (earthquake.url.isNotBlank()) {
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(earthquake.url))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("View on USGS Website", color = Color.White)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun DetailCard(
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

@Composable
fun DetailRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(it, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text = label, color = TextSecondary, fontSize = 13.sp)
        }
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
fun DepthBar(depth: Double, color: Color) {
    val maxDepth = 700.0
    val fillFraction = (depth / maxDepth).coerceIn(0.0, 1.0).toFloat()

    Box(
        modifier = Modifier
            .width(24.dp)
            .height(120.dp)
            .background(SurfaceVariant, RoundedCornerShape(12.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((120 * fillFraction).dp)
                .align(Alignment.BottomCenter)
                .background(color, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
        )
    }
}

fun getTectonicHint(earthquake: Earthquake): String {
    val place = earthquake.place.lowercase()
    return when {
        earthquake.depth < 70 && (place.contains("pacific") || place.contains("ring")) ->
            "Likely interplate subduction zone event near plate boundary"
        earthquake.depth < 70 ->
            "Shallow crustal event — typically occurs along tectonic plate boundaries or fault systems"
        earthquake.depth in 70.0..300.0 ->
            "Intermediate depth — may indicate subducting slab activity in a convergent boundary zone"
        else ->
            "Deep earthquake — occurs within subducting oceanic slabs far below the surface"
    }
}

fun formatFullDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss z", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
