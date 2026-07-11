package com.autocaller.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autocaller.ui.theme.StatusRunning
import com.autocaller.ui.theme.StatusStopped
import com.autocaller.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val phoneNumber by viewModel.phoneNumber.collectAsStateWithLifecycle()
    val intervalMinutes by viewModel.intervalMinutes.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startCalling() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun hasCallPermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CALL_PHONE
    ) == PackageManager.PERMISSION_GRANTED

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AutoCaller", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            StatusBanner(isRunning)
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = viewModel::onPhoneNumberChange,
                label = { Text("Phone Number") },
                placeholder = { Text("+91 98765 43210") },
                leadingIcon = { Icon(Icons.Default.Phone, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Include country code") }
            )
            IntervalSlider(intervalMinutes, viewModel::onIntervalChange, !isRunning)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        if (hasCallPermission()) viewModel.startCalling()
                        else callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                    },
                    enabled = !isRunning,
                    modifier = Modifier.weight(1f)
                ) { Text("Start Calling") }
                OutlinedButton(
                    onClick = viewModel::stopCalling,
                    enabled = isRunning,
                    modifier = Modifier.weight(1f)
                ) { Text("Stop Calling") }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("How it works", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "AutoCaller places a call every $intervalMinutes minute(s). If a call is in progress it waits. " +
                        if (isRunning) "Running in background." else "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(isRunning: Boolean) {
    val color by animateColorAsState(
        if (isRunning) StatusRunning else StatusStopped,
        tween(400), label = ""
    )
    Surface(shape = MaterialTheme.shapes.medium, color = color, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), Alignment.CenterVertically, Arrangement.Center) {
            Box(Modifier.size(10.dp).background(Color.White, MaterialTheme.shapes.extraSmall))
            Spacer(Modifier.width(10.dp))
            Text(
                if (isRunning) "Service Running" else "Service Stopped",
                color = Color.White, style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun IntervalSlider(value: Int, onValueChange: (Int) -> Unit, enabled: Boolean) {
    Column {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Call Interval", fontWeight = FontWeight.Medium)
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text("$value min", Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
        Slider(value.toFloat(), { onValueChange(it.toInt()) }, Modifier.fillMaxWidth(), enabled = enabled, valueRange = 1f..60f, steps = 58)
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("1 min", style = MaterialTheme.typography.labelMedium)
            Text("60 min", style = MaterialTheme.typography.labelMedium)
        }
    }
}
