package com.inputblocker.pctool

import androidx.compose.foundation.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.inputblocker.shared.GhostTap
import com.inputblocker.shared.ClusterUtils
import com.inputblocker.shared.Region
import com.inputblocker.pctool.ADBHelper
import com.inputblocker.pctool.PresetManager
import com.inputblocker.pctool.Preset
import com.inputblocker.pctool.LiveEvent

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
    var showGallery by remember { mutableStateOf(false) }
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
                                             regions = regions.toMutableList().apply {
                                                 this[selectedRegionIndex!!] = r.copyWithCoords(newLeft, newTop, newLeft + r.width, newTop + r.height)
                                             }
                                             regions = regions.toList()
                                        } else if (isResizing && selectedRegionIndex != null) {
                                             val r = regions[selectedRegionIndex!!]
                                             val nx = change.position.x / size.width
                                             val ny = change.position.y / size.height
                                             regions = regions.toMutableList().apply {
                                                 this[selectedRegionIndex!!] = when (resizeHandleIndex) {
                                                     0 -> r.copy(x1 = nx, y1 = ny)
                                                     1 -> r.copy(x2 = nx, y1 = ny)
                                                     2 -> r.copy(x1 = nx, y2 = ny)
                                                     3 -> r.copy(x2 = nx, y2 = ny)
                                                     else -> r
                                                 }
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

            if (showGallery) {
                Dialog(onDismissRequest = { showGallery = false }) {
                    Surface(
                        modifier = Modifier.width(400.dp).fillMaxHeight(0.7f),
                        color = BgPanel,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Preset Gallery", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                            
                            Box(modifier = Modifier.weight(1f)) {
                                val galleryDir = java.io.File("presets")
                                if (!galleryDir.exists() || galleryDir.listFiles()?.isEmpty() == true) {
                                    Text("No presets found in /presets folder.", color = TextMuted, modifier = Modifier.align(Alignment.Center))
                                } else {
                                    // Simple list of presets
                                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                        galleryDir.listFiles { _, name -> name.endsWith(".ibpreset") }?.forEach { file ->
                                            val preset = PresetManager.importPreset(file)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                                    .background(BgDark, RoundedCornerShape(4.dp))
                                                    .clickable {
                                                        if (preset != null) {
                                                            regions = preset.regions
                                                            blockingEnabled = preset.enabled
                                                            crashProtection = preset.safeMode
                                                            status = "Applied preset: ${preset.name}"
                                                            showGallery = false
                                                        }
                                                    }
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(preset?.name ?: file.name, color = TextMain, fontSize = 14.sp)
                                                Text("Apply", color = AccentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                            
                            Button(
                                onClick = { showGallery = false },
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                colors = ButtonDefaults.buttonColors(backgroundColor = BorderColor)
                            ) {
                                Text("Close", color = TextMain)
                            }
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
                                                  val clusters = ClusterUtils.clusterTaps(tapsInRegion, 0.05f, 3)
                                                  if (clusters.isNotEmpty()) {
                                                      val mainCluster = clusters.maxByOrNull { it.size }!!
                                                      
                                                       // Update Region Bounds & Thresholds
                                                       val newBounds = ClusterUtils.calculateBoundingBox(mainCluster)
                                                       val (suggestedP, suggestedD) = ClusterUtils.suggestThresholds(mainCluster)
                                                       regions = regions.toMutableList().apply {
                                                           this[selectedRegionIndex!!] = selectedRegion.copy(
                                                               x1 = newBounds.x1,
                                                               y1 = newBounds.y1,
                                                               x2 = newBounds.x2,
                                                               y2 = newBounds.y2,
                                                               minPressure = suggestedP,
                                                               maxDuration = suggestedD
                                                           )
                                                       }.toList()
                                                       status = "Auto-tuned based on ${mainCluster.size} taps"
                                                  } else {
                                                      status = "Too few taps for clustering"
                                                  }
                                              } else {
                                                  status = "No logs found for this region"
                                              }
                                          },
                                          modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                          colors = ButtonDefaults.buttonColors(backgroundColor = AccentColor)
                                      ) { Text("Auto-Tune", color = Color.Black) }

                                     
                                      Text("Min Pressure:", color = TextMuted, fontSize = 12.sp)
                                      TextField(
                                          value = selectedRegion.minPressure.toString(), 
                                          onValueChange = { 
                                              val p = it.toFloatOrNull() ?: 0f
                                              regions = regions.toMutableList().apply {
                                                  this[selectedRegionIndex!!] = selectedRegion.copy(minPressure = p)
                                              }
                                              regions = regions.toList() 
                                          }, 
                                          modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                          colors = TextFieldDefaults.textFieldColors(backgroundColor = BgDark)
                                      )
                                     
                                      Text("Max Duration (ms):", color = TextMuted, fontSize = 12.sp)
                                      TextField(
                                          value = selectedRegion.maxDuration.toString(), 
                                          onValueChange = { 
                                              val d = it.toLongOrNull() ?: 1000L
                                              regions = regions.toMutableList().apply {
                                                  this[selectedRegionIndex!!] = selectedRegion.copy(maxDuration = d)
                                              }
                                              regions = regions.toList() 
                                          }, 
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
                        Button(
                            onClick = {
                                try {
                                    val file = java.io.File("profile_backup.conf")
                                    val config = StringBuilder()
                                    config.appendLine("# InputBlocker Profile Backup")
                                    regions.forEach { config.appendLine(it.toString()) }
                                    file.writeText(config.toString())
                                    status = "Profile saved to profile_backup.conf"
                                } catch (e: Exception) {
                                    status = "Save failed: ${e.message}"
                                }
                            }, modifier = Modifier.width(110.dp)
                        ) { Text("Save Profile") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                try {
                                    val file = java.io.File("profile_backup.conf")
                                    if (!file.exists()) {
                                        status = "No backup file found"
                                        return@Button
                                    }
                                    val newRegions = mutableListOf<Region>()
                                    file.readLines().forEach { line ->
                                        if (line.trim().isNotEmpty() && !line.startsWith("#")) {
                                            Region.fromString(line)?.let { newRegions.add(it) }
                                        }
                                    }
                                    regions = newRegions
                                    selectedRegionIndex = null
                                    status = "Profile loaded from file"
                                } catch (e: Exception) {
                                    status = "Load failed: ${e.message}"
                                }
                            }, modifier = Modifier.width(110.dp)
                        ) { Text("Load Profile") }
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
                                try {
                                    val chooser = javax.swing.JFileChooser()
                                    chooser.dialogTitle = "Export Preset"
                                    chooser.selectedFile = java.io.File("my_preset.ibpreset")
                                    if (chooser.showSaveDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                                        val file = chooser.selectedFile
                                        val preset = Preset("My Preset", blockingEnabled, crashProtection, regions)
                                        if (PresetManager.exportPreset(file, preset)) {
                                            status = "Preset exported to ${file.name}"
                                            
                                            // Also save to gallery
                                            val galleryDir = java.io.File("presets")
                                            if (!galleryDir.exists()) galleryDir.mkdirs()
                                            PresetManager.exportPreset(java.io.File(galleryDir, file.name), preset)
                                        } else {
                                            status = "Export failed"
                                        }
                                    }
                                } catch (e: Exception) {
                                    status = "Export error: ${e.message}"
                                }
                            }, modifier = Modifier.width(110.dp)
                        ) { Text("Export Preset") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                showGallery = true
                            }, modifier = Modifier.width(110.dp)
                        ) { Text("Gallery") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                try {
                                    val chooser = javax.swing.JFileChooser()
                                    chooser.dialogTitle = "Import Preset"
                                    if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                                        val file = chooser.selectedFile
                                        val preset = PresetManager.importPreset(file)
                                        if (preset != null) {
                                            regions = preset.regions
                                            blockingEnabled = preset.enabled
                                            crashProtection = preset.safeMode
                                            status = "Imported preset: ${preset.name}"
                                        } else {
                                            status = "Invalid preset file"
                                        }
                                    }
                                } catch (e: Exception) {
                                    status = "Import error: ${e.message}"
                                }
                            }, modifier = Modifier.width(110.dp)
                        ) { Text("Import Preset") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                adbHelper?.let { helper ->
                                    val success = helper.pushConfig(regions, blockingEnabled, crashProtection)
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
}

fun main() {
    // Resolve the installation directory so crash_logs goes next to the EXE, not inside the JRE
    val installDir = try {
        // Use an anonymous object's class to locate the app jar at runtime.
        // In a jpackage'd app this lives at {installDir}/app/pc-tool-kotlin-jvm-*.jar
        val jarUrl = (object {}).javaClass.protectionDomain.codeSource.location
        val jarFile = java.io.File(jarUrl.toURI())
        if (jarFile.name.endsWith(".jar")) {
            // Navigate: .../app/some.jar → .../app/ → {installDir}
            jarFile.parentFile?.parentFile ?: java.io.File(".")
        } else {
            // Development mode – class files, use CWD
            java.io.File(".")
        }
    } catch (_: Exception) {
        // Ultimate fallback
        java.io.File(".")
    }
    val crashDir = java.io.File(installDir, "crash_logs").also { it.mkdirs() }
    val crashLog = java.io.File(crashDir, "inputblocker_crash.log")
    val tee = System.out // keep original stdout
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        crashLog.writeText("""=== CRASH on thread: ${thread.name} ===
Time: ${java.util.Date()}
${throwable.stacktraceToString()}
""")
        tee.println("FATAL: ${throwable.message}")
        throwable.printStackTrace(tee)
        tee.println("Crash written to: ${crashLog.absolutePath}")
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "InputBlocker Designer",
            state = rememberWindowState(width = 800.dp, height = 900.dp)
        ) {
            App()
        }
    }
}

// Kotlin stdlib doesn't expose stacktraceToString in older versions
private fun Throwable.stacktraceToString(): String {
    val sw = java.io.StringWriter()
    val pw = java.io.PrintWriter(sw)
    printStackTrace(pw)
    pw.flush()
    return sw.toString()
}
