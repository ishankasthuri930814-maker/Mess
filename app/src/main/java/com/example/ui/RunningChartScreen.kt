package com.example.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.RunningChartRecord
import com.example.ui.theme.BadgeGold
import com.example.ui.theme.DutyActiveGreen
import com.example.ui.theme.DutyOffOrange
import com.example.ui.theme.PoliceNavyDark
import com.example.ui.theme.PoliceNavyPrimary
import com.example.util.DateTimeUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun RunningChartScreen(
    viewModel: PoliceViewModel
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("ගණකය", "ඉතිහාසය", "සාරාංශය")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PoliceNavyDark)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(DutyActiveGreen.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = DutyActiveGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "දිනපතා ගමන් සටහන් (Running Chart)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "දිනපතා වියදම් සහ ගමන් සටහන්",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Navigation Tabs (ගණකය, ඉතිහාසය, සාරාංශය)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    val tabIcon = when (index) {
                        0 -> Icons.Default.Calculate
                        1 -> Icons.Default.History
                        else -> Icons.Default.TrendingUp
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) DutyActiveGreen else Color.Transparent
                            )
                            .clickable { selectedTab = index }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = tabIcon,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            when (selectedTab) {
                0 -> RunningChartCalculatorTab(
                    viewModel = viewModel,
                    onSaved = { selectedTab = 1 }
                )
                1 -> RunningChartHistoryTab(viewModel = viewModel)
                2 -> RunningChartSummaryTab(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun RunningChartCalculatorTab(
    viewModel: PoliceViewModel,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var dateStr by remember { mutableStateOf(DateTimeUtils.getTodayKey()) }
    var vehicleNumber by remember { mutableStateOf("") }
    var startMeterStr by remember { mutableStateOf("") }
    var endMeterStr by remember { mutableStateOf("") }
    var kmPerLiterStr by remember { mutableStateOf("7.0") }
    var startFuelStr by remember { mutableStateOf("45.0") }
    var addedFuelStr by remember { mutableStateOf("0.0") }
    var notes by remember { mutableStateOf("") }

    val distinctVehicles by viewModel.distinctVehicles.collectAsStateWithLifecycle()

    // DatePicker
    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val m = (month + 1).toString().padStart(2, '0')
            val d = dayOfMonth.toString().padStart(2, '0')
            dateStr = "$year-$m-$d"
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    // Auto-fetch latest meter and fuel when vehicle is entered
    fun checkAutoFill(veh: String) {
        if (veh.isBlank()) return
        coroutineScope.launch {
            val latest = viewModel.getLatestRecordForVehicle(veh)
            if (latest != null) {
                if (startMeterStr.isBlank()) {
                    startMeterStr = latest.endMeter.toString()
                }
                if (startFuelStr == "45.0" || startFuelStr.isBlank()) {
                    startFuelStr = latest.remainingFuel.toString()
                }
                if (kmPerLiterStr == "7.0") {
                    kmPerLiterStr = latest.kmPerLiter.toString()
                }
            }
        }
    }

    // Calculations
    val startMeter = startMeterStr.toDoubleOrNull() ?: 0.0
    val endMeter = endMeterStr.toDoubleOrNull() ?: startMeter
    val kmPerLiter = kmPerLiterStr.toDoubleOrNull() ?: 7.0
    val startFuel = startFuelStr.toDoubleOrNull() ?: 0.0
    val addedFuel = addedFuelStr.toDoubleOrNull() ?: 0.0

    val distanceKm = remember(startMeter, endMeter) {
        max(0.0, endMeter - startMeter)
    }

    val consumedFuel = remember(distanceKm, kmPerLiter) {
        if (kmPerLiter > 0) distanceKm / kmPerLiter else 0.0
    }

    val remainingFuel = remember(startFuel, addedFuel, consumedFuel) {
        startFuel + addedFuel - consumedFuel
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Date Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { datePickerDialog.show() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "දිනය (YYYY-MM-DD)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = dateStr,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                IconButton(onClick = { dateStr = DateTimeUtils.getTodayKey() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Date",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Vehicle Plate input
        Column {
            OutlinedTextField(
                value = vehicleNumber,
                onValueChange = {
                    vehicleNumber = it
                    checkAutoFill(it)
                },
                label = { Text("වාහනයේ නම / අංකය (Vehicle Plate No)") },
                placeholder = { Text("e.g. KX 1493") },
                leadingIcon = {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = DutyActiveGreen)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vehicle_number_input")
            )

            if (distinctVehicles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(distinctVehicles) { veh ->
                        FilterChip(
                            selected = vehicleNumber.equals(veh, ignoreCase = true),
                            onClick = {
                                vehicleNumber = veh
                                checkAutoFill(veh)
                            },
                            label = { Text(veh, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        // Row 1: Start Meter & Km/L
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = startMeterStr,
                onValueChange = { startMeterStr = it },
                label = { Text("ආරම්භක මීටරය (km)") },
                placeholder = { Text("e.g. 123456") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("start_meter_input")
            )

            OutlinedTextField(
                value = kmPerLiterStr,
                onValueChange = { kmPerLiterStr = it },
                label = { Text("ලීටරයකින් (km/L)") },
                placeholder = { Text("e.g. 7.0") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("km_per_liter_input")
            )
        }

        // Row 2: Starting Fuel & Added Fuel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = startFuelStr,
                onValueChange = { startFuelStr = it },
                label = { Text("ආරම්භක ඉන්ධන (L)") },
                placeholder = { Text("e.g. 45.0") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("start_fuel_input")
            )

            OutlinedTextField(
                value = addedFuelStr,
                onValueChange = { addedFuelStr = it },
                label = { Text("ලබාගත් ඉන්ධන (L)") },
                placeholder = { Text("e.g. 14.0") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("added_fuel_input")
            )
        }

        // Row 3: End Meter
        OutlinedTextField(
            value = endMeterStr,
            onValueChange = { endMeterStr = it },
            label = { Text("අවසාන මීටරය (km) - Ending Meter") },
            placeholder = { Text("e.g. 123526") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("end_meter_input")
        )

        // Calculated Result Cards (Matching Screenshot 1)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Consumed Fuel Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DutyOffOrange))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalGasStation,
                        contentDescription = null,
                        tint = DutyOffOrange,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "භාවිත කල ඉන්ධන",
                        fontSize = 11.sp,
                        color = DutyOffOrange,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "%.1f L".format(consumedFuel),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DutyOffOrange
                    )
                }
            }

            // Driven Distance Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DutyActiveGreen))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = DutyActiveGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ධාවනය කළ දුර",
                        fontSize = 11.sp,
                        color = DutyActiveGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "%.1f km".format(distanceKm),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DutyActiveGreen
                    )
                }
            }
        }

        // Remaining Fuel Banner
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ටැංකියේ ඉතිරි ඉන්ධන ප්‍රමාණය:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "%.1f Liters".format(remainingFuel),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Action Buttons: Clear & Save
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    vehicleNumber = ""
                    startMeterStr = ""
                    endMeterStr = ""
                    notes = ""
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("හිස් කරන්න")
            }

            Button(
                onClick = {
                    if (vehicleNumber.isBlank()) {
                        return@Button
                    }
                    viewModel.saveRunningChartRecord(
                        date = dateStr,
                        vehicleNumber = vehicleNumber,
                        startMeter = startMeter,
                        endMeter = endMeter,
                        kmPerLiter = kmPerLiter,
                        startFuel = startFuel,
                        addedFuel = addedFuel,
                        notes = notes,
                        onSuccess = onSaved
                    )
                },
                enabled = vehicleNumber.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = DutyActiveGreen),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("save_running_chart_btn")
            ) {
                Text("Next / සුරකින්න", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun RunningChartHistoryTab(
    viewModel: PoliceViewModel
) {
    val records by viewModel.runningChartRecords.collectAsStateWithLifecycle()
    val vehicles by viewModel.distinctVehicles.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedVehicleFilter.collectAsStateWithLifecycle()

    var recordToDelete by remember { mutableStateOf<RunningChartRecord?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp)
    ) {
        // Vehicle Filter Chips
        Text(
            text = "වාහනය අනුව පෙරන්න (Filter by Vehicle)",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { viewModel.setVehicleFilter("ALL") },
                    label = { Text("සියල්ල") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    )
                )
            }
            items(vehicles) { veh ->
                FilterChip(
                    selected = selectedFilter == veh,
                    onClick = { viewModel.setVehicleFilter(veh) },
                    label = { Text(veh) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (records.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ගමන් සටහන් කිසිවක් නැත",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    RunningChartHistoryCard(
                        record = record,
                        onDelete = { recordToDelete = record }
                    )
                }
            }
        }
    }

    if (recordToDelete != null) {
        val record = recordToDelete!!
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("ගමන් සටහන ඉවත් කිරීම") },
            text = { Text("${record.vehicleNumber} (${record.date}) ගමන් සටහන ඉවත් කිරීමට ඔබට විශ්වාසද?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRunningChartRecord(record)
                        recordToDelete = null
                    }
                ) {
                    Text("මකන්න", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text("අවලංගු කරන්න")
                }
            }
        )
    }
}

@Composable
fun RunningChartHistoryCard(
    record: RunningChartRecord,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Vehicle Plate & Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = BadgeGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = record.vehicleNumber,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = record.date,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BadgeGold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Meter details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "ආරම්භක මීටරය", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "%.1f km".format(record.startMeter), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(text = "අවසාන මීටරය", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "%.1f km".format(record.endMeter), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "ධාවනය කළ දුර", fontSize = 11.sp, color = DutyActiveGreen)
                    Text(
                        text = "%.1f km".format(record.distanceKm),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DutyActiveGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Fuel details (Matching Screenshot 2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "ආරම්භක", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "%.1f L".format(record.startFuel), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(text = "ලබාගත්", fontSize = 11.sp, color = DutyActiveGreen)
                    Text(text = "+%.1f L".format(record.addedFuel), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DutyActiveGreen)
                }
                Column {
                    Text(text = "දැවුණු", fontSize = 11.sp, color = DutyOffOrange)
                    Text(text = "-%.1f L".format(record.consumedFuel), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DutyOffOrange)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "ඉතිරි ඉන්ධන", fontSize = 11.sp, color = BadgeGold)
                    Text(text = "%.1f L".format(record.remainingFuel), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BadgeGold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Delete action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RunningChartSummaryTab(
    viewModel: PoliceViewModel
) {
    val records by viewModel.runningChartRecords.collectAsStateWithLifecycle()
    val vehicles by viewModel.distinctVehicles.collectAsStateWithLifecycle()
    var selectedVehicle by remember { mutableStateOf(vehicles.firstOrNull() ?: "") }

    LaunchedEffect(vehicles) {
        if (selectedVehicle.isBlank() && vehicles.isNotEmpty()) {
            selectedVehicle = vehicles.first()
        }
    }

    val vehicleRecords = records.filter {
        selectedVehicle.isBlank() || it.vehicleNumber.equals(selectedVehicle, ignoreCase = true)
    }

    val totalKm = vehicleRecords.sumOf { it.distanceKm }
    val totalAdded = vehicleRecords.sumOf { it.addedFuel }
    val totalConsumed = vehicleRecords.sumOf { it.consumedFuel }
    val latestRecord = vehicleRecords.maxByOrNull { it.timestamp }
    val remainingFuel = latestRecord?.remainingFuel ?: 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "වාහනය තෝරන්න (Select Vehicle)",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(vehicles) { veh ->
                FilterChip(
                    selected = selectedVehicle.equals(veh, ignoreCase = true),
                    onClick = { selectedVehicle = veh },
                    label = { Text(veh) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        // Summary Card (Matching Screenshot 4)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = DateTimeUtils.getCurrentMonthYear() + if (selectedVehicle.isNotBlank()) " ($selectedVehicle)" else "",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                SummaryRowItem(
                    index = "1.",
                    label = "පසුගිය මාසයේ ඉතිරි ඉන්ධන ප්‍රමාණය",
                    value = "%.1f Liters".format(0.0),
                    icon = Icons.Default.LocalGasStation,
                    color = MaterialTheme.colorScheme.onSurface
                )

                SummaryRowItem(
                    index = "2.",
                    label = "මෙම මාසයේ ලබාගත් ඉන්ධන ප්‍රමාණය",
                    value = "+%.1f Liters".format(totalAdded),
                    icon = Icons.Default.LocalGasStation,
                    color = DutyActiveGreen
                )

                SummaryRowItem(
                    index = "3.",
                    label = "මෙම මාසයේ දැවුණු ඉන්ධන ප්‍රමාණය",
                    value = "-%.1f Liters".format(totalConsumed),
                    icon = Icons.Default.LocalGasStation,
                    color = DutyOffOrange
                )

                SummaryRowItem(
                    index = "4.",
                    label = "මෙම මාසයේ ධාවනය කළ කිලෝමීටර ගණන",
                    value = "%.1f km".format(totalKm),
                    icon = Icons.Default.Speed,
                    color = MaterialTheme.colorScheme.primary
                )

                SummaryRowItem(
                    index = "5.",
                    label = "මෙම මාසයේ ඉතිරි ඉන්ධන ප්‍රමාණය",
                    value = "%.1f Liters".format(remainingFuel),
                    icon = Icons.Default.LocalGasStation,
                    color = BadgeGold
                )
            }
        }
    }
}

@Composable
fun SummaryRowItem(
    index: String,
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = index, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
