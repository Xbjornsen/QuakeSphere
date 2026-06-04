package com.quakesphere.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quakesphere.domain.model.DepthCategory
import com.quakesphere.domain.model.Earthquake
import com.quakesphere.domain.model.EarthquakeSwarm
import com.quakesphere.ui.globe.formatDepth
import com.quakesphere.ui.globe.formatTimeAgo
import com.quakesphere.ui.globe.magnitudeColor
import com.quakesphere.ui.globe.magnitudeTextColor
import com.quakesphere.ui.theme.DepthDeep
import com.quakesphere.ui.theme.DepthIntermediate
import com.quakesphere.ui.theme.DepthShallow
import com.quakesphere.ui.theme.ElectricBlue
import com.quakesphere.ui.theme.MagStrong
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
    val snackbarHostState  = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { viewModel.clearError(); snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Earthquakes", color = TextPrimary, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    if (uiState.isLoading || uiState.isRefreshing) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(22.dp),
                            color       = ElectricBlue,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, "Refresh", tint = ElectricBlue)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SpaceBlack)
            )
        },
        snackbarHost    = { SnackbarHost(snackbarHostState) },
        containerColor  = SpaceBlack
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Magnitude filter chips ──────────────────────────────────────
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.filterOptions) { mag ->
                    FilterChip(
                        selected = uiState.minMagnitude == mag,
                        onClick  = { viewModel.setMinMagnitude(mag) },
                        label    = {
                            Text(
                                text  = "M${if (mag == mag.toLong().toDouble()) mag.toInt() else mag}+",
                                color = if (uiState.minMagnitude == mag) Color.White else TextSecondary,
                                fontSize = 13.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricBlue,
                            containerColor         = SurfaceVariant
                        )
                    )
                }
            }

            // ── Stats row ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("${uiState.earthquakes.size} earthquakes", color = TextSecondary, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (uiState.swarms.isNotEmpty()) {
                        Text(
                            text       = "${uiState.swarms.size} swarm${if (uiState.swarms.size > 1) "s" else ""}",
                            color      = Color(0xFFFFBB33),
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    val tsunamiCount = uiState.earthquakes.count { it.tsunami == 1 }
                    if (tsunamiCount > 0) {
                        Text(
                            "$tsunamiCount tsunami",
                            color      = TsunamiWarning,
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ── Tab row ───────────────────────────────────────────────────
            val tabs = listOf("Latest", "Table", "Swarms")
            val selectedTab = uiState.viewMode.ordinal

            TabRow(
                selectedTabIndex    = selectedTab,
                containerColor      = SpaceBlack,
                contentColor        = ElectricBlue,
                indicator           = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier  = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color     = ElectricBlue
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { viewModel.setViewMode(ListViewMode.values()[index]) },
                        text     = {
                            Text(
                                text  = title,
                                color = if (selectedTab == index) ElectricBlue else TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // ── Tab content ───────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxSize()) {
                when (uiState.viewMode) {
                    ListViewMode.CARDS  -> CardsTab(uiState, onNavigateToDetail)
                    ListViewMode.TABLE  -> TableTab(uiState, viewModel, onNavigateToDetail)
                    ListViewMode.SWARMS -> SwarmsTab(uiState, onNavigateToDetail)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Tab: CARDS (original list view)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CardsTab(
    uiState: ListUiState,
    onNavigateToDetail: (String) -> Unit
) {
    if (uiState.earthquakes.isEmpty() && !uiState.isLoading) {
        EmptyState("No earthquakes found", "Pull down to refresh")
    } else {
        LazyColumn(
            modifier              = Modifier.fillMaxSize(),
            verticalArrangement   = Arrangement.spacedBy(8.dp),
            contentPadding        = androidx.compose.foundation.layout.PaddingValues(
                top = 8.dp, bottom = 24.dp
            )
        ) {
            items(uiState.earthquakes, key = { it.id }) { quake ->
                EarthquakeListItem(
                    earthquake = quake,
                    useMiles   = uiState.useMiles,
                    onClick    = { onNavigateToDetail(quake.id) },
                    modifier   = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Tab: TABLE (sortable data grid)
// ══════════════════════════════════════════════════════════════════════════════

private val COL_MAG   = 52.dp
private val COL_DEPTH = 64.dp
private val COL_TIME  = 72.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TableTab(
    uiState: ListUiState,
    viewModel: EarthquakeListViewModel,
    onNavigateToDetail: (String) -> Unit
) {
    if (uiState.earthquakes.isEmpty() && !uiState.isLoading) {
        EmptyState("No earthquakes found", "Pull down to refresh"); return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Sticky header
        stickyHeader {
            TableHeader(uiState.sortColumn, uiState.sortAscending, viewModel::setSortColumn)
        }
        itemsIndexed(uiState.earthquakes, key = { _, q -> q.id }) { index, quake ->
            TableRow(
                quake       = quake,
                isEven      = index % 2 == 0,
                useMiles    = uiState.useMiles,
                onClick     = { onNavigateToDetail(quake.id) }
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TableHeader(
    sortColumn: SortColumn,
    sortAscending: Boolean,
    onSort: (SortColumn) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D1A2D))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SortableHeader("Mag",      COL_MAG,   SortColumn.MAGNITUDE, sortColumn, sortAscending, onSort)
        SortableHeader("Location", null,       SortColumn.PLACE,     sortColumn, sortAscending, onSort,
            modifier = Modifier.weight(1f))
        SortableHeader("Depth",    COL_DEPTH, SortColumn.DEPTH,     sortColumn, sortAscending, onSort)
        SortableHeader("Time",     COL_TIME,  SortColumn.TIME,      sortColumn, sortAscending, onSort)
    }
}

@Composable
private fun SortableHeader(
    label: String,
    width: androidx.compose.ui.unit.Dp?,
    column: SortColumn,
    activeColumn: SortColumn,
    ascending: Boolean,
    onSort: (SortColumn) -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = activeColumn == column
    val baseModifier = if (width != null) modifier.width(width) else modifier
    Row(
        modifier = baseModifier
            .clickable { onSort(column) }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text       = label,
            color      = if (isActive) ElectricBlue else TextSecondary,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Bold
        )
        if (isActive) {
            Icon(
                imageVector = if (ascending) Icons.Default.KeyboardArrowUp
                              else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint   = ElectricBlue,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun TableRow(
    quake: Earthquake,
    isEven: Boolean,
    useMiles: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isEven) Color(0xFF111C2E) else Color(0xFF0E1828)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Magnitude
        Box(
            modifier         = Modifier.width(COL_MAG),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(magnitudeColor(quake.mag)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = String.format("%.1f", quake.mag),
                    color      = magnitudeTextColor(quake.mag),
                    fontWeight = FontWeight.Bold,
                    fontSize   = 12.sp
                )
            }
        }

        // Location
        Text(
            text     = quake.place,
            color    = TextPrimary,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 6.dp)
        )

        // Depth
        val depthColor = when (quake.depthCategory) {
            DepthCategory.SHALLOW      -> DepthShallow
            DepthCategory.INTERMEDIATE -> DepthIntermediate
            DepthCategory.DEEP         -> DepthDeep
        }
        Text(
            text      = formatDepth(quake.depth, useMiles),
            color     = depthColor,
            fontSize  = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier  = Modifier.width(COL_DEPTH),
            textAlign = TextAlign.Center
        )

        // Time
        Text(
            text      = formatTimeAgo(quake.time),
            color     = TextSecondary,
            fontSize  = 11.sp,
            modifier  = Modifier.width(COL_TIME),
            textAlign = TextAlign.End
        )
    }
    HorizontalDivider(color = Color(0xFF1A2740), thickness = 0.5.dp)
}

// ══════════════════════════════════════════════════════════════════════════════
// Tab: SWARMS
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SwarmsTab(
    uiState: ListUiState,
    onNavigateToDetail: (String) -> Unit
) {
    if (uiState.swarms.isEmpty()) {
        EmptyState(
            title    = "No swarms detected",
            subtitle = "Swarms require ≥ 3 earthquakes within 200 km and 48 h"
        )
        return
    }
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding      = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 12.dp, vertical = 10.dp
        )
    ) {
        items(uiState.swarms, key = { it.id }) { swarm ->
            SwarmCard(swarm = swarm, onEventClick = onNavigateToDetail)
        }
    }
}

@Composable
private fun SwarmCard(
    swarm: EarthquakeSwarm,
    onEventClick: (String) -> Unit
) {
    Card(
        colors    = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape     = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            // ── Header ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(magnitudeColor(swarm.maxMagnitude)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text       = String.format("%.1f", swarm.maxMagnitude),
                            color      = magnitudeTextColor(swarm.maxMagnitude),
                            fontWeight = FontWeight.Bold,
                            fontSize   = 14.sp
                        )
                        Text(
                            text  = "max",
                            color = magnitudeTextColor(swarm.maxMagnitude).copy(alpha = 0.7f),
                            fontSize = 8.sp
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = swarm.location,
                        color      = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 14.sp,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text     = "${swarm.eventCount} events · ${swarm.durationHours}h duration",
                        color    = TextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text     = "Started ${formatTimeAgo(swarm.startTime)}",
                        color    = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                // Event count badge
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFBB33).copy(alpha = 0.18f))
                        .border(1.dp, Color(0xFFFFBB33), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = "${swarm.eventCount}",
                        color      = Color(0xFFFFBB33),
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = SurfaceVariant)
            Spacer(Modifier.height(10.dp))

            // ── Stacked magnitude visualiser ─────────────────────────────────
            Text(
                text     = "Events by magnitude",
                color    = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Vertical string: largest at top, drawn as a horizontal bar stack
            swarm.events.forEach { event ->
                SwarmEventRow(event = event, maxMag = swarm.maxMagnitude,
                    onClick = { onEventClick(event.id) })
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SwarmEventRow(
    event: Earthquake,
    maxMag: Double,
    onClick: () -> Unit
) {
    val barFraction = (event.mag / maxMag.coerceAtLeast(1.0)).toFloat().coerceIn(0.1f, 1f)
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Magnitude badge
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(magnitudeColor(event.mag)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = String.format("%.1f", event.mag),
                color      = magnitudeTextColor(event.mag),
                fontSize   = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(SurfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(barFraction)
                    .clip(RoundedCornerShape(5.dp))
                    .background(magnitudeColor(event.mag).copy(alpha = 0.85f))
            )
        }

        // Time
        Text(
            text     = formatTimeAgo(event.time),
            color    = TextSecondary,
            fontSize = 10.sp,
            modifier = Modifier.width(52.dp),
            textAlign = TextAlign.End
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Card list item (shared)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun EarthquakeListItem(
    earthquake: Earthquake,
    useMiles: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier  = modifier.fillMaxWidth().clickable { onClick() },
        colors    = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(magnitudeColor(earthquake.mag)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = String.format("%.1f", earthquake.mag),
                    color      = magnitudeTextColor(earthquake.mag),
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = earthquake.place,
                    color      = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp,
                    maxLines   = 2
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    val depthColor = when (earthquake.depthCategory) {
                        DepthCategory.SHALLOW      -> DepthShallow
                        DepthCategory.INTERMEDIATE -> DepthIntermediate
                        DepthCategory.DEEP         -> DepthDeep
                    }
                    Text(formatDepth(earthquake.depth, useMiles), color = depthColor,
                        fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("·", color = TextSecondary, fontSize = 12.sp)
                    Text(formatTimeAgo(earthquake.time), color = TextSecondary, fontSize = 12.sp)
                    if (earthquake.tsunami == 1) {
                        Text("·", color = TextSecondary, fontSize = 12.sp)
                        Text("TSUNAMI", color = TsunamiWarning,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                text       = earthquake.magnitudeCategory.label,
                color      = magnitudeColor(earthquake.mag),
                fontSize   = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                contentDescription = null,
                tint     = TextSecondary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(title,    color = TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}
