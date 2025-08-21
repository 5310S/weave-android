package com.example.weave_andriod

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WeaveApp()
        }
    }
}

@Composable
fun WeaveApp(viewModel: P2PManager = viewModel()) {
    var bootstrapHost by remember { mutableStateOf("") }
    var peerID by remember { mutableStateOf("") }
    var outgoing by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.connectionStatus) {
        showError = viewModel.connectionStatus.contains("failed", ignoreCase = true) ||
                viewModel.connectionStatus.contains("error", ignoreCase = true) ||
                viewModel.connectionStatus == "Invalid peer ID"
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Node ID: ${viewModel.nodeID}")
            Text("Address: ${if (viewModel.publicAddress.isEmpty()) "?" else viewModel.publicAddress}:${viewModel.publicPort}")
            Text("Status: ${viewModel.connectionStatus}")
            Button(onClick = {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Address", "${viewModel.publicAddress}:${viewModel.publicPort}"))
            }) {
                Text("Copy Address")
            }
            Button(
                onClick = { viewModel.fetchPublicIP() },
                modifier = Modifier.opacity(if (viewModel.publicAddress.isEmpty()) 1.0 else 0.0)
            ) {
                Text("Retry Address Fetch")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = bootstrapHost,
                    onValueChange = { bootstrapHost = it },
                    label = { Text("Bootstrap Host") },
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    viewModel.joinNetwork(bootstrapHost, 9999)
                    viewModel.storePublicAddress()
                }) {
                    Text("Join Network")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = peerID,
                    onValueChange = { peerID = it },
                    label = { Text("Peer ID") },
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    val id = peerID.toULongOrNull()
                    if (id != null) {
                        viewModel.connectToPeer(id)
                    } else {
                        viewModel.connectionStatus = "Invalid peer ID"
                        showError = true
                    }
                }) {
                    Text("Connect")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = outgoing,
                    onValueChange = { outgoing = it },
                    label = { Text("Message") },
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    viewModel.send(outgoing)
                    outgoing = ""
                }) {
                    Text("Send")
                }
            }
            LazyColumn {
                items(viewModel.messages) { msg ->
                    Text(msg)
                }
            }
            if (showError) {
                AlertDialog(
                    onDismissRequest = { showError = false },
                    title = { Text("Error") },
                    text = { Text(viewModel.connectionStatus) },
                    confirmButton = {
                        Button(onClick = {
                            viewModel.fetchPublicIP()
                            showError = false
                        }) {
                            Text("Retry")
                        }
                    },
                    dismissButton = {
                        Button(onClick = { showError = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}