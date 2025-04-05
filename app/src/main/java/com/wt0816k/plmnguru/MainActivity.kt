package com.wt0816k.plmnguru

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wt0816k.plmnguru.ui.theme.PlmnGuruTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlmnGuruTheme {
                MainScreen()
            }
        }
    }
}

@Preview(showBackground = true)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val activity = LocalActivity.current

    var menuExpanded by remember { mutableStateOf(false) }
    var isListView by remember { mutableStateOf(true) }

    var isInputRunnerActive by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var job: Job? by remember { mutableStateOf(null) }

    val device = "/dev/smd7"
    val output = remember { mutableStateOf(emptyList<String>()) }
    val networks = remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(isInputRunnerActive) {
        if (isInputRunnerActive) {
            val process = Runtime.getRuntime().exec("su")
            process.waitFor(500, TimeUnit.MILLISECONDS)
            val outputStream = process.outputStream

            job = scope.launch {
                Log.i("InputRunner", "Launched")
                while (isActive) {
                    withContext(Dispatchers.IO) {
                        val command = "echo -e \"AT+COPS=?\\r\" > $device\n"
                        outputStream.write(command.toByteArray())
                        outputStream.flush()

                        Log.i("InputRunner", "Executed: $command")
                        delay(2000)
                    }
                }
                process.destroy()
                Log.i("InputRunner", "Stopped")
            }
        } else {
            job?.cancel()
        }
    }

    LaunchedEffect(key1 = Unit) {
        withContext(Dispatchers.IO) {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat", device))
            launch {
                try {
                    process.inputStream.bufferedReader().use { bufferedReader ->
                        while (isActive) {
                            val readText = bufferedReader.readLine()?.ifEmpty { null } ?: continue
                            output.value += readText

                            if (readText.contains("+COPS:")) {
                                val regex = """\((.*?)\)""".toRegex()
                                networks.value = emptyList()
                                networks.value =
                                    regex.findAll(readText).map { it.groupValues[1].replace("\"", "") }
                                        .filter { it.contains(Regex("\\d{5,}")) }
                                        .toList()
                                networks.value.forEach { s: String -> Log.i("Debug", s) }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Shell", e.message.toString())
                }
            }
            try {
                awaitCancellation()
            } finally {
                process.destroy()
            }
        }
    }

    PlmnGuruTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Plmn Guru",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    actions = {
                        if (!isListView) {
                            IconButton(onClick = { output.value = emptyList() }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Clear")
                            }
                        }
                        IconButton(onClick = { isInputRunnerActive = !isInputRunnerActive }) {
                            Icon(
                                if (!isInputRunnerActive) painterResource(R.drawable.baseline_play_arrow_24) else painterResource(R.drawable.baseline_pause_24),
                                contentDescription = "Start / Stop"
                            )
                        }
                        IconButton(onClick = { }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Run network scan (once)") },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.baseline_search_24),
                                        contentDescription = "Run network scan (once)"
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    scanNetwork(device)
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Auto connect to network") },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.baseline_find_replace_24),
                                        contentDescription = "Detach from network"
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    autoAttachNetwork(device)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Detach from network") },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.outline_do_not_disturb_on_24),
                                        contentDescription = "Detach from network"
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    detachNetwork(device)
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Exit") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Exit") },
                                onClick = {
                                    menuExpanded = false
                                    activity?.finishAndRemoveTask()
                                }
                            )
                        }
                        IconButton(onClick = { menuExpanded = !menuExpanded }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }

                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { isListView = !isListView },
                    modifier = Modifier
                        .padding(end = 0.dp, bottom = 0.dp)
                ) {
                    Icon(
                        if (!isListView) painterResource(R.drawable.baseline_format_list_bulleted_24) else painterResource(R.drawable.baseline_terminal_24),
                        contentDescription = "Switch View"
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                if (isListView) {
                    LazyColumn {
                        items(networks.value) { network ->
                            val items = network.split(",")
                            val registerState = items[0].toInt()
                            val longName = items[1]
                            val shortName = items[2]
                            val plmn = items[3].toInt()
                            val rat = items[4].toInt()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        attachNetwork(device, plmn)
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Icon(
                                    painter = when (registerState) {
                                        0 -> painterResource(R.drawable.baseline_help_outline_24)
                                        1 -> painterResource(R.drawable.outline_circle_24)
                                        2 -> painterResource(R.drawable.baseline_check_circle_outline_24)
                                        3 -> painterResource(R.drawable.baseline_block_24)
                                        else -> painterResource(R.drawable.baseline_help_outline_24)
                                    },
                                    tint = when (registerState) {
                                        2 -> Color(0xff24c94e)
                                        3 -> Color(0xffc9242f)
                                        else -> Color.Unspecified
                                    },
                                    contentDescription = "Register State",
                                    modifier = Modifier
                                        .padding(start = 10.dp, end = 10.dp)
                                )
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .padding(top = 7.dp)
                                    ) {
                                        Text(
                                            text = plmn.toString(),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .padding(end = 8.dp)
                                        )
                                        Text(
                                            text = when (rat) {
                                                0 -> "GSM"
                                                2 -> "3G"
                                                4 -> "HSDPA"
                                                5 -> "HSUPA"
                                                6 -> "HSDPA/HSUPA"
                                                7 -> "4G"
                                                9 -> "NB-IoT"
                                                12 -> "5G"
                                                else -> "Unknown($rat)"
                                            },
                                            modifier = Modifier
                                                .padding(end = 8.dp)
                                        )
                                        Text(
                                            text = when (registerState) {
                                                0 -> "(Unknown)"
                                                1 -> "(Available)"
                                                2 -> "(Current)"
                                                3 -> "(Forbidden)"
                                                else -> "(Unknown)"
                                            },
                                            modifier = Modifier,
                                            fontWeight = if (registerState == 2) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .padding(bottom = 7.dp)
                                    ) {
                                        Text(
                                            text = "$longName - $shortName",
                                            modifier = Modifier
                                                .fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn {
                        items(output.value) { item ->
                            Text(
                                text = item,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp)
                            )
                            HorizontalDivider(color = LocalContentColor.current.copy(alpha = 0.07f))
                        }
                    }
                }
            }
        }
    }
}

private fun scanNetwork(device: String) {
    CoroutineScope(Dispatchers.IO).launch {
        withContext(Dispatchers.IO) {
            Runtime.getRuntime().exec(
                arrayOf("su", "-c", "echo", "-e", """ "AT+COPS=?\r" """, ">", device)
            )
        }
    }
}

private fun attachNetwork(device: String, plmn: Int) {
    CoroutineScope(Dispatchers.IO).launch {
        withContext(Dispatchers.IO) {
            Runtime.getRuntime().exec(
                arrayOf("su", "-c", "echo", "-e", """ "AT+COPS=1,2,\"$plmn\"\r" """, ">", device)
            )
        }
    }
}

private fun autoAttachNetwork(device: String) {
    CoroutineScope(Dispatchers.IO).launch {
        withContext(Dispatchers.IO) {
            Runtime.getRuntime().exec(
                arrayOf("su", "-c", "echo", "-e", """ "AT+COPS=0\r" """, ">", device)
            )
        }
    }
}

private fun detachNetwork(device: String) {
    CoroutineScope(Dispatchers.IO).launch {
        withContext(Dispatchers.IO) {
            Runtime.getRuntime().exec(
                arrayOf("su", "-c", "echo", "-e", """ "AT+COPS=2\r" """, ">", device)
            )
        }
    }
}