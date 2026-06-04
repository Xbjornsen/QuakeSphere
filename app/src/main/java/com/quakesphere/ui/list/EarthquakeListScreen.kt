package com.quakesphere.ui.list

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quakesphere.domain.model.DepthCategory
import com.quakesphere.domain.model.Earthquake
import com.quakesphere.ui.globe.formatTimeAgo
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarthquakeListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    viewModel: EarthquakeListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(Unit) {
            viewModel.refresh()
        }
    }

    LaunchedEffect(uiState.isRefreshing) {
        if (!uiState.isRefreshing) {
            pullToRefreshState.endRefresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Earthquake List", color = TextPrimary, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SpaceBlack),
                actions = {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 16.dp),
                            color = ElectricBlue,
                            strokeWidth = 2.dp
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SpaceBlack
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Filter chips
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.filterOptions) { magFilter ->
                        FilterChip(
                            selected = uiState.minMagnitude == magFilter,
                            onClick = { viewModel.setMinMagnitude(magFilter) },
                            label = {
                                Text(
                                    text = "M${magFilter.toInt()}+",
                                    color = if (uiState.minMagnitude == magFilter) Color.White else TextSecondary
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricBlue,
                                containerColor = SurfaceVariant
                            )
                        )
                    }
                }

                // Stats row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${uiState.earthquakes.size} earthquakes",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    val tsunamiCount = uiState.earthquakes.count { it.tsunami == 1 }
                    if (tsunamiCount > 0) {
                        Text(
                            text = "$tsunamiCount tsunami warnings",
                            color = TsunamiWarning,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                if (uiState.earthquakes.isEmpty() && !uiState.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text("No earthquakes found", color = TextSecondary, fontSize = 16.sp)
                            Text("Pull down to refresh", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.earthquakes, key = { it.id }) { quake ->
                            EarthquakeListItem(
                                earthquake = quake,
                                onClick = { onNavigateToDetail(quake.id) },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }

            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = SurfaceCard,
                contentColor = ElectricBlue
            )
        }
    }
}

@Composable
fun EarthquakeListItem(
    earthquake: Earthquake,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Magnitude badge
            val magColor = magnitudeColor(earthquake.mag)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(magColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format("%.1f", earthquake.mag),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            // Quake info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = earthquake.place,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 2
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Depth indicator
                    val depthColor = when (earthquake.depthCategory) {
                        DepthCategory.SHALLOW -> DepthShallow
                        DepthCategory.INTERMEDIATE -> DepthIntermediate
                        DepthCategory.DEEP -> DepthDeep
                    }
                    Text(
                        text = "${earthquake.depth.toInt()} km",
                        color = depthColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(text = "•", color = TextSecondary, fontSize = 12.sp)
                    Text(
                        text = formatTimeAgo(earthquake.time),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    if (earthquake.tsunami == 1) {
                        Text(text = "•", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            text = "TSUNAMI",
                            color = TsunamiWarning,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Magnitude category label
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = earthquake.magnitudeCategory.label,
                    color = magnitudeColor(earthquake.mag),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
