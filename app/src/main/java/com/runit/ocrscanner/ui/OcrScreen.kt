package com.runit.ocrscanner.ui

import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.simulateHotReload
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.runit.ocrscanner.Beep
import com.runit.ocrscanner.CsvWriter
import com.runit.ocrscanner.ScanStore
import com.runit.ocrscanner.SsidParser
import com.runit.ocrscanner.model.ScanArea
import com.runit.ocrscanner.model.ScanResult
import com.runit.ocrscanner.ui.theme.AppTheme
import com.runit.ocrscanner.ui.theme.BackgroundColor
import com.runit.ocrscanner.ui.theme.SuccessColor
import com.runit.ocrscanner.ui.theme.TextColor
import com.runit.ocrscanner.ui.theme.WarningColor

@Composable
fun OcrScreen() {
    val context = LocalContext.current
    val store = remember {
        ScanStore(context)
    }

    val csvWriter = remember {
        CsvWriter(context)
    }

    val beep = remember {
        Beep()
    }

    DisposableEffect(beep) {
        onDispose {
            beep.close()
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasPermission = granted
        }
    )

    var enable by remember {
        mutableStateOf(true)
    }

    var text by remember {
        mutableStateOf("")
    }

    var pending by remember {
        mutableStateOf<String?>(null)
    }

    var hits by remember {
        mutableIntStateOf(0)
    }

    var lastAcceptedTime by remember {
        mutableLongStateOf(0L)
    }

    var ssidExists by remember {
        mutableStateOf(false)
    }
    var count by remember {
        mutableIntStateOf(store.count())
    }

    DisposableEffect(csvWriter, beep) {
        onDispose {
            csvWriter.close()
            beep.close()
        }
    }

    fun handleOcr(raw: String, cb: (success: Boolean) -> Unit = {}) {
        text = SsidParser.parse(raw) ?: ""

        val code = SsidParser.parse(raw)

        if (code == null) {
            pending = null
            hits = 0
            return
        }

        if (pending == code) {
            hits++
        } else {
            pending = code
            hits = 1
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastAcceptedTime < 2000L) return

        lastAcceptedTime = now
        pending = null
        hits = 0

        val row = ScanResult(code, System.currentTimeMillis())
        cb(store.add(row))
    }

    OcrContent(
        text = text,
        count = count,
        enable = enable,
        exists = ssidExists,
        hasPermission = hasPermission,
        onResult = { result ->
            handleOcr(result) {
                ssidExists = !it
                if (it) {
                    count = store.count()
                    beep.play()
                }
            }
        },
        onRequestPermission = {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        },
        onClear = {
            count = 0
            store.clear()
        },
        onShare = {
            csvWriter.share(
                store.list()
            ) { error ->
                Log.e("OcrScreen", "Share failed", error)
            }
        },
    )
}

@Composable
fun OcrContent(
    text: String,
    exists: Boolean,
    enable: Boolean,
    count: Int,
    hasPermission: Boolean,
    onResult: (String) -> Unit,
    onRequestPermission: () -> Unit,
    onClear: () -> Unit,
    onShare: () -> Unit,
    isPreview: Boolean = false
) {
    val scanArea = remember {
        ScanArea(
            width = 0.9f,
            height = 0.35f
        )
    }

    var scale by remember {
        mutableFloatStateOf(2f)
    }

    var flash by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.DarkGray)
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when {
                isPreview -> {
                    OcrPlaceHolder()
                }

                !hasPermission -> {
                    Button(
                        onClick = onRequestPermission,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Grant".uppercase())
                    }
                }

                else -> {
                    CameraUI(
                        scale = scale,
                        enabled = enable,
                        flash = flash,
                        scanArea = scanArea,
                        onResult = onResult
                    )

                    ScanOverlay(
                        scanArea = scanArea,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    InfoOverlay(
                        text = text,
                        exists = exists,
                        scanArea = scanArea,
                    )
                }
            }
        }
        InfoBar(
            count = count,
            scale = scale,
        ) {
            scale = if (scale >= 6f) 1f else scale + 1
        }
        ActionMenu(
            onFlash = { flash = !flash },
            onClear = onClear,
            onShare = onShare
        )
    }
}

@Composable
fun InfoBar(
    count: Int,
    scale: Float,
    onScaleClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF202526)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = null,
                tint = Color.LightGray,
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Đã quét: $count",
                color = Color.White,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Surface(
                onClick = onScaleClick,
                shape = RoundedCornerShape(50),
                color = Color(0xFF263B29),
                border = BorderStroke(
                    1.dp,
                    Color(0xFF54885A)
                )
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = null,
                        tint = Color(0xFF92F77C),
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(Modifier.width(6.dp))

                    Text(
                        text = "OCR x${scale.toInt()}",
                        color = Color(0xFF92F77C),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionMenu(
    onFlash: () -> Unit,
    onClear: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val actions = listOf(
            Triple("ĐÈN", Icons.Default.FlashOn, onFlash),
            Triple("XÓA", Icons.Default.DeleteOutline, onClear),
            Triple("CHIA SẺ", Icons.Default.Share, onShare),
        )

        actions.forEach { (label, icon, action) ->
            OutlinedButton(
                onClick = action,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(
                    horizontal = 12.dp,
                    vertical = 8.dp
                ),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Color(0xFF92F77C)
                    )
                    Text(
                        text = label,
                        maxLines = 1,
                        fontSize = 12.sp,
                        color = Color(0xFFFFFFFF)
                    )
                }

            }
        }
    }
}

@Composable
private fun InfoOverlay(
    text: String?,
    exists: Boolean,
    scanArea: ScanArea,
    modifier: Modifier = Modifier
) {
    if (text.isNullOrBlank()) return
    val iconColor = if (exists) WarningColor else SuccessColor

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val top = maxHeight * (1f - scanArea.height) / 2f

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = top - 46.dp)
                .background(BackgroundColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically

        ) {
            Icon(
                imageVector = if (exists) Icons.Default.Wifi else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                color = TextColor,
                maxLines = 1,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ScanOverlay(
    scanArea: ScanArea,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth(scanArea.width)
            .fillMaxHeight(scanArea.height)
            .border(
                2.dp,
                Color.Green,
                RoundedCornerShape(8.dp)
            )
    )
}


@Composable
private fun OcrPlaceHolder() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = Color.White
        )
        Text(
            "Camera Preview",
            color = Color.White
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun OcrScreenPreview() {
    AppTheme {
        OcrContent(
            text = "SSID: Viettel_ABCD\nSerial:123456789"
                .repeat(3),
            exists = false,
            enable = true,
            count = 0,
            hasPermission = true,
            onResult = {},
            onRequestPermission = {},
            onClear = {},
            onShare = {},
            isPreview = true
        )
    }
}
