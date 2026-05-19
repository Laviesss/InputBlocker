import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.inputblocker.shared.Region

// Theme Colors
val BgDark = Color(0xFF121212)
val BgPanel = Color(0xFF1E1E1E)
val AccentColor = Color(0xFFBB86FC)
val BorderColor = Color(0xFF333333)
val TextMain = Color.White
val TextMuted = Color(0xFFB0B0B0)
val RegionFill = Color(0xFFB388FF).copy(alpha = 0.3f)
val RegionStroke = Color(0xFFB388FF)
val DrawStroke = Color(0xFF448AFF)
val DrawFill = Color(0xFF448AFF).copy(alpha = 0.3f)

@Composable
fun App() {
    var adbHelper by remember { mutableStateOf<ADBHelper?>(null) }
    var status by remember { mutableStateOf("Connecting...") }
    var regions by remember { mutableStateOf(listOf<Region>()) }
    var blockingEnabled by remember { mutableStateOf(true) }
    var crashProtection by remember { mutableStateOf(true) }
    var showHeatmap by remember { mutableStateOf(false) }
    var ghostTaps by remember { mutableStateOf(listOf<GhostTap>()) }
    var liveEvents by remember { mutableStateOf(listOf<LiveEvent>()) }
    var isLivePreviewEnabled by remember { mutableStateOf(false) }
    var selectedRegionIndex by remember { mutableStateOf<Int?>(null) }
    
    // Drawing/Dragging State
    var isDrawing by remember { mutableStateOf(false) }
    var drawStart by remember { mutableStateOf(Offset.Zero) }
    var drawEnd by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isResizing by remember { mutableStateOf(false) }
    var resizeHandleIndex by remember { mutableStateOf(-1) }

    // Initialize ADB
    LaunchedEffect(Unit) {
        val helper = ADBHelper()
        adbHelper = helper
        status = if (helper.connected) "Connected: ${helper.deviceSerial}" else "Not Connected"
        regions = helper.getCurrentConfig()
    }

    LaunchedEffect(isLivePreviewEnabled) {
        if (isLivePreviewEnabled) {
            adbHelper?.let { helper ->
                helper.streamLiveEvents { event ->
                    liveEvents = (liveEvents + event).takeLast(20)
                }
            }
        } else {
            liveEvents = emptyList()
        }
    }

    MaterialTheme(
        colors = darkColors(
            primary = AccentColor,
            background = BgDark,
            surface = BgPanel,
            onBackground = TextMain,
            onSurface = TextMain
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = BgDark) {
            Column(modifier = Modifier.fillMaxSize()) {
                // --- TOP TOOLBAR ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgPanel)
                        .border(0.dp, BorderColor)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = status,
                        color = if (status.contains("Connected")) Color(0xFF90EE90) else Color(0xFFFF6B6B),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Regions: ${regions.size}",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = blockingEnabled, onCheckedChange = { blockingEnabled = it })
                            Text("Blocking", color = TextMain, fontSize = 12.sp)
                            Spacer(Modifier.width(12.dp))
                            Checkbox(checked = crashProtection, onCheckedChange = { crashProtection = it })
                            Text("SafeMode", color = TextMain, fontSize = 12.sp)
                            Spacer(Modifier.width(12.dp))
                            Checkbox(checked = showHeatmap, onCheckedChange = { showHeatmap = it })
                            Text("Heatmap", color = TextMain, fontSize = 12.sp)
                            Spacer(Modifier.width(12.dp))
                            Checkbox(checked = isLivePreviewEnabled, onCheckedChange = { isLivePreviewEnabled = it })
                            Text("Live", color = TextMain, fontSize = 12.sp)
                        }

                }

                // --- MIDDLE CONTENT ---
                Row(modifier = Modifier.weight(1f)) {
                    // Main Canvas Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(BgDark)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = { offset ->
                                        val nx = offset.x / size.width
                                        val ny = offset.y / size.height
                                        
                                        if (selectedRegionIndex != null) {
                                            val r = regions[selectedRegionIndex!!]
                                            val hSize = 0.01f
                                            if (Math.abs(nx - r.left) < hSize && Math.abs(ny - r.top) < hSize) {
                                                resizeHandleIndex = 0; isResizing = true; isDragging = false; return@detectTapGestures
                                            }
                                            if (Math.abs(nx - r.right) < hSize && Math.abs(ny - r.top) < hSize) {
                                                resizeHandleIndex = 1; isResizing = true; isDragging = false; return@detectTapGestures
                                            }
                                            if (Math.abs(nx - r.left) < hSize && Math.abs(ny - r.bottom) < hSize) {
                                                resizeHandleIndex = 2; isResizing = true; isDragging = false; return@detectTapGestures
                                            }
                                            if (Math.abs(nx - r.right) < hSize && Math.abs(ny - r.bottom) < hSize) {
                                                resizeHandleIndex = 3; isResizing = true; isDragging = false; return@detectTapGestures
                                            }
                                        }

                                        var found = false
                                        for (i in regions.indices.reversed()) {
                                            if (regions[i].contains(nx, ny)) {
                                                selectedRegionIndex = i
                                                isDragging = true
                                                dragOffset = Offset(
                                                    offset.x - (regions[i].left * size.width),
                                                    offset.y - (regions[i].top * size.height)
                                                )
                                                found = true
                                                break
                                            }
                                        }

                                        if (!found) {
                                            selectedRegionIndex = null
                                            isDrawing = true
                                            drawStart = offset
                                            drawEnd = offset
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (isDrawing) {
                                            drawEnd = drawEnd + dragAmount
                                        } else if (isDragging && selectedRegionIndex != null) {
                                            val r = regions[selectedRegionIndex!!]
                                            val newLeft = (change.position.x - dragOffset.x) / size.width
                                            val newTop = (change.position.y - dragOffset.y) / size.height
                                            r.SetCoords(newLeft, newTop, newLeft + r.width, newTop + r.height)
                                            regions = regions.toList()
                                        } else if (isResizing && selectedRegionIndex != null) {
                                            val r = regions[selectedRegionIndex!!]
                                            val nx = change.position.x / size.width
                                            val ny = change.position.y / size.height
                                            when (resizeHandleIndex) {
                                                0 -> { r.x1 = nx; r.y1 = ny }
                                                1 -> { r.x2 = nx; r.y1 = ny }
                                                2 -> { r.x1 = nx; r.y2 = ny }
                                                3 -> { r.x2 = nx; r.y2 = ny }
                                            }
                                            regions = regions.toList()
                                        }
                                    },
                                    onDragEnd = {
                                        if (isDrawing) {
                                            val x1 = Math.min(drawStart.x, drawEnd.x) / size.width
                                            val y1 = Math.min(drawStart.y, drawEnd.y) / size.height
                                            val x2 = Math.max(drawStart.x, drawEnd.x) / size.width
                                            val y2 = Math.max(drawStart.y, drawEnd.y) / size.height
                                            if (Math.abs(x2 - x1) > 0.01f && Math.abs(y2 - y1) > 0.01f) {
                                                regions = regions + Region(x1, y1, x2, y2)
                                            }
                                        }
                                        isDrawing = false
                                        isDragging = false
                                        isResizing = false
                                        resizeHandleIndex = -1
                                    }
                                )
                            }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                regions.forEachIndexed { index, r ->
                                    val isSelected = index == selectedRegionIndex
                                    val color = if (r.isExclude) Color(0x4DFF0000) else RegionFill
                                    val strokeColor = if (r.isExclude) Color(0xFFFF0000) else if (isSelected) Color.White else RegionStroke
                                    val strokeWidth = if (isSelected) 4f else 3f

                                    val left = r.left * size.width
                                    val top = r.top * size.height
                                    val right = r.right * size.width
                                    val bottom = r.bottom * size.height

                                    if (r.type == 0) {
                                        drawRect(color = color, topLeft = Offset(left, top), size = Size(right - left, bottom - top))
                                        drawRect(color = strokeColor, topLeft = Offset(left, top), size = Size(right - left, bottom - top), style = Stroke(width = strokeWidth))
                                    } else if (r.type == 1) {
                                        val cx = r.x1 * size.width
                                        val cy = r.y1 * size.height
                                        val radius = r.x2 * size.width
                                        drawCircle(color = color, center = Offset(cx, cy), radius = radius)
                                        drawCircle(color = strokeColor, center = Offset(cx, cy), radius = radius, style = Stroke(width = strokeWidth))
                                    } else if (r.type == 2) {
                                        val cx = r.x1 * size.width
                                        val cy = r.y1 * size.height
                                        val rx = r.x2 * size.width
                                        val ry = r.y2 * size.height
                                        drawOval(color = color, topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2, ry * 2))
                                        drawOval(color = strokeColor, topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2, ry * 2), style = Stroke(width = strokeWidth))
                                     }
                                
                                    if (showHeatmap) {
                                        ghostTaps.forEach { tap ->
                                            drawCircle(
                                                color = Color.Red.copy(alpha = 0.2f),
                                                radius = 10f,
                                                center = Offset(tap.x * size.width, tap.y * size.height)
                                            )
                                        }
                                    }
                                
                                    if (isLivePreviewEnabled) {
                                        liveEvents.forEach { event ->
                                            val color = if (event.type == "BLOCK") Color.Red else Color.Green
                                            drawCircle(
                                                color = color.copy(alpha = 0.5f),
                                                radius = 8f,
                                                center = Offset(event.x * size.width, event.y * size.height)
                                            )
                                        }
                                    }
                                
                                    if (isSelected) {

                                        val hSize = 6f
                                        drawRect(Color.White, Offset(left - hSize/2, top - hSize/2), Size(hSize, hSize))
                                        drawRect(Color.White, Offset(right - hSize/2, top - hSize/2), Size(hSize, hSize))
                                        drawRect(Color.White, Offset(left - hSize/2, bottom - hSize/2), Size(hSize, hSize))
                                        drawRect(Color.White, Offset(right - hSize/2, bottom - hSize/2), Size(hSize, hSize))
                                    }
                                }

                                if (isDrawing) {
                                    val x1 = Math.min(drawStart.x, drawEnd.x)
                                    val y1 = Math.min(drawStart.y, drawEnd.y)
                                    val x2 = Math.max(drawStart.x, drawEnd.x)
                                    val y2 = Math.max(drawStart.y, drawEnd.y)
                                    drawRect(color = DrawFill, topLeft = Offset(x1, y1), size = Size(x2 - x1, y2 - y1))
                                    drawRect(color = DrawStroke, topLeft = Offset(x1, y1), size = Size(x2 - x1, y2 - y1), style = Stroke(width = 3f))
                                }
                            }
                        }
                    }

                    if (selectedRegionIndex != null) {
                                 val selectedRegion = regions[selectedRegionIndex!!]
                                 Column(
                                     modifier = Modifier
                                         .width(220.dp)
                                         .fillMaxHeight()
                                         .background(BgPanel)
                                         .border(1.dp, BorderColor)
                                         .padding(16.dp)
                                 ) {
                                     Text(
                                         text = "Region Properties",
                                         color = TextMain,
                                         fontSize = 16.sp,
                                         fontWeight = FontWeight.Bold,
                                         modifier = Modifier.padding(bottom = 16.dp)
                                     )
                                     
                                     Button(
                                         onClick = {
                                             val tapsInRegion = ghostTaps.filter { it.x in selectedRegion.left..selectedRegion.right && it.y in selectedRegion.top..selectedRegion.bottom }
                                             if (tapsInRegion.isNotEmpty()) {
                                                 val maxP = tapsInRegion.maxOf { it.pressure }
                                                 val minD = tapsInRegion.minOf { it.duration }
                                                 selectedRegion.minPressure = maxP + 0.01f
                                                 selectedRegion.maxDuration = (minD - 50).coerceAtLeast(100L)
                                                 regions = regions.toList()
                                                 status = "Tuned region from ${tapsInRegion.size} taps"
                                             } else {
                                                 status = "No logs found for this region"
                                             }
                                         },
                                         modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                         colors = ButtonDefaults.buttonColors(backgroundColor = AccentColor)
                                     ) { Text("Suggest Tuning", color = Color.Black) }
                                     
                                     Text("Min Pressure:", color = TextMuted, fontSize = 12.sp)
                                     TextField(
                                         value = selectedRegion.minPressure.toString(), 
                                         onValueChange = { val p = it.toFloatOrNull() ?: 0f; selectedRegion.minPressure = p; regions = regions.toList() }, 
                                         modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                         colors = TextFieldDefaults.textFieldColors(backgroundColor = BgDark)
                                     )
                                     
                                     Text("Max Duration (ms):", color = TextMuted, fontSize = 12.sp)
                                     TextField(
                                         value = selectedRegion.maxDuration.toString(), 
                                         onValueChange = { val d = it.toLongOrNull() ?: 1000L; selectedRegion.maxDuration = d; regions = regions.toList() }, 
                                         modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                         colors = TextFieldDefaults.textFieldColors(backgroundColor = BgDark)
                                     )
                                 }

                    }
                }

                // --- BOTTOM BAR ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgPanel)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Left-click drag to draw | Right-click to delete | R=Undo C=Clear S=Save Space=Refresh",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = {
                            adbHelper?.let {
                                regions = it.getCurrentConfig()
                                status = "Refreshed config"
                            }
                        }, modifier = Modifier.width(90.dp)) { Text("Refresh") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            if (regions.isNotEmpty()) {
                                regions = regions.dropLast(1)
                                selectedRegionIndex = null
                                status = "Undo last region"
                            }
                        }, modifier = Modifier.width(90.dp)) { Text("Undo") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                regions = emptyList()
                                selectedRegionIndex = null
                                status = "Cleared all regions"
                            }, 
                            modifier = Modifier.width(90.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFC62828))
                        ) { Text("Clear All") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {}, modifier = Modifier.width(90.dp)) { Text("Save Profile") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {}, modifier = Modifier.width(90.dp)) { Text("Load Profile") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                adbHelper?.let {
                                    val zip = File("InputBlocker.zip")
                                    if (zip.exists()) {
                                        val success = it.installModule(zip)
                                        status = if (success) "Module installed! Reboot required." else "Install failed"
                                    } else {
                                        status = "InputBlocker.zip not found in root"
                                    }
                                }
                            }, modifier = Modifier.width(130.dp)
                        ) { Text("Install Module") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                adbHelper?.let {
                                    val latencies = it.pullLatencyLog()
                                    if (latencies.isNotEmpty()) {
                                        val avg = latencies.average() / 1000.0
                                        val p99 = latencies.sorted()[(latencies.size * 0.99).toInt()] / 1000.0
                                        status = "Avg: ${"%.2f".format(avg)}µs | p99: ${"%.2f".format(p99)}µs"
                                    } else {
                                        status = "No latency logs found"
                                    }
                                }
                            }, modifier = Modifier.width(150.dp)
                        ) { Text("Profile Latency") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                adbHelper?.let {
                                    val taps = it.pullBlockLog()
                                    ghostTaps = taps
                                    showHeatmap = true
                                    status = "Analyzed ${taps.size} ghost taps"
                                }
                            }, modifier = Modifier.width(120.dp)
                        ) { Text("Analyze Logs") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                // Integration with PresetManager will go here
                                status = "Preset system coming soon"
                            }, modifier = Modifier.width(110.dp)
                        ) { Text("Export Preset") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                // Integration with PresetManager will go here
                                status = "Preset system coming soon"
                            }, modifier = Modifier.width(110.dp)
                        ) { Text("Import Preset") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                adbHelper?.let {
                                    val success = it.pushConfig(regions, blockingEnabled, crashProtection)
                                    status = if (success) "Config pushed!" else "Push failed"
                                }
                            }, 
                            modifier = Modifier.width(110.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2E7D32))
                        ) { Text("Push to Device") }
                    }
                    
                    Text(
                        "Ready",
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    fun main() = application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "InputBlocker Setup - Kotlin Edition",
            state = rememberWindowState(width = 800.dp, height = 900.dp)
        ) {
            App()
        }
    }
}
