package com.example.sisyphusapp

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

private const val BASE_URL = "http://192.168.4.1:5000"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SisyphusApp()
        }
    }
}

@Composable
fun SisyphusApp() {

    var tabIndex by remember { mutableIntStateOf(0) }

    val tabs = listOf("Control","LED","Patterns")

    Column {

        TabRow(selectedTabIndex = tabIndex) {

            tabs.forEachIndexed { index, title ->

                Tab(
                    selected = tabIndex == index,
                    onClick = { tabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        when(tabIndex){

            0 -> ControlTab()
            1 -> LedTab()
            2 -> PatternsTab()
        }
    }
}

@Composable
fun ControlTab() {

    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Ready") }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text("Machine Control")

        Button(onClick = { scope.launch { status = callApi("/grbl/home") } }) {
            Text("HOME")
        }

        Button(onClick = { scope.launch { status = callApi("/grbl/unlock") } }) {
            Text("UNLOCK")
        }

        Button(onClick = { scope.launch { status = callApi("/pattern/pause") } }) {
            Text("PAUSE")
        }

        Button(onClick = { scope.launch { status = callApi("/pattern/resume") } }) {
            Text("RESUME")
        }

        Button(onClick = { scope.launch { status = callApi("/pattern/stop") } }) {
            Text("STOP")
        }

        Text(status)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedTab() {

    val scope = rememberCoroutineScope()

    val effects = remember { mutableStateListOf<String>() }

    var selectedEffect by remember { mutableIntStateOf(0) }

    var expanded by remember { mutableStateOf(false) }

    var brightness by remember { mutableFloatStateOf(128f) }
    var speed by remember { mutableFloatStateOf(128f) }
    var intensity by remember { mutableFloatStateOf(128f) }

    var result by remember { mutableStateOf("LED ready") }

    LaunchedEffect(Unit) {

        val data = callApi("/led/effects")

        effects.clear()
        effects.addAll(parseSimpleJsonArray(data))
    }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Row {

            Button(
                onClick = { scope.launch { result = callApi("/led/on") } },
                modifier = Modifier.weight(1f)
            ) {
                Text("ON")
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { scope.launch { result = callApi("/led/off") } },
                modifier = Modifier.weight(1f)
            ) {
                Text("OFF")
            }
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {

            OutlinedTextField(
                value = effects.getOrNull(selectedEffect) ?: "Loading",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                label = { Text("Effect") }
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {

                effects.forEachIndexed { index, name ->

                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {

                            selectedEffect = index
                            expanded = false

                            scope.launch {

                                result = callApi("/led/effect?id=$index")
                            }
                        }
                    )
                }
            }
        }

        Text("Brightness ${brightness.toInt()}")

        Slider(
            value = brightness,
            onValueChange = { brightness = it },
            valueRange = 0f..255f,
            onValueChangeFinished = {
                scope.launch {
                    result = callApi("/led/brightness?v=${brightness.toInt()}")
                }
            }
        )

        Text("Speed ${speed.toInt()}")

        Slider(
            value = speed,
            onValueChange = { speed = it },
            valueRange = 0f..255f,
            onValueChangeFinished = {
                scope.launch {
                    result = callApi("/led/speed?v=${speed.toInt()}")
                }
            }
        )

        Text("Intensity ${intensity.toInt()}")

        Slider(
            value = intensity,
            onValueChange = { intensity = it },
            valueRange = 0f..255f,
            onValueChangeFinished = {
                scope.launch {
                    result = callApi("/led/intensity?v=${intensity.toInt()}")
                }
            }
        )

        Text(result)
    }
}

@Composable
fun PatternsTab() {

    val scope = rememberCoroutineScope()

    val patterns = remember { mutableStateListOf<String>() }

    var info by remember { mutableStateOf("Loading patterns") }

    var selected by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {

        val data = callApi("/pattern/list")

        patterns.clear()
        patterns.addAll(parseSimpleJsonArray(data))
    }

    Column(modifier = Modifier.padding(16.dp)) {

        Button(
            onClick = {

                selected?.let {

                    scope.launch {

                        info = callApi("/pattern/run?name=$it")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("RUN PATTERN")
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {

            items(patterns) { item ->

                Button(
                    onClick = { selected = item },
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Text(item)
                }
            }
        }

        Text(info)
    }
}

suspend fun callApi(path:String):String = withContext(Dispatchers.IO) {

    try {

        val url = URL("$BASE_URL$path")

        val conn = url.openConnection() as HttpURLConnection

        conn.requestMethod = "GET"

        val reader = BufferedReader(InputStreamReader(conn.inputStream))

        val text = reader.readText()

        reader.close()

        text

    } catch (e:Exception){

        "Error ${e.message}"
    }
}

fun parseSimpleJsonArray(json:String):List<String>{

    return json
        .removePrefix("[")
        .removeSuffix("]")
        .split(",")
        .map{ it.trim().replace("\"","") }
}