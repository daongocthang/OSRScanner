package com.runit.ocrscanner.ui

import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.runit.ocrscanner.ScanStore
import com.runit.ocrscanner.SsidParser
import com.runit.ocrscanner.model.ScanArea
import com.runit.ocrscanner.model.ScanResult
import com.runit.ocrscanner.ui.theme.AppTheme

@Composable
fun OcrScreen() {
    val context = LocalContext.current
    val store = remember {
        ScanStore(context)
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
    var flash by remember {
        mutableStateOf(false)
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

    fun handleOcr(raw: String, cb: (success: Boolean) -> Unit = {}) {
        text = raw

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
        flash = flash,
        exists = ssidExists,
        enable = enable,
        hasPermission = hasPermission,
        onResult = { result ->
            handleOcr(result) {
                ssidExists = !it
            }
        },
        onScanToggle = {
            enable = !enable
        },
        onFlashToggle = {
            flash = !flash
        },
        onRequestPermission = {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    )
}

@Composable
fun OcrContent(
    text: String,
    exists: Boolean,
    flash: Boolean,
    enable: Boolean,
    hasPermission: Boolean,
    onResult: (String) -> Unit,
    onScanToggle: () -> Unit,
    onFlashToggle: () -> Unit,
    onRequestPermission: () -> Unit,
    isPreview: Boolean = false
) {
    val scanArea = remember {
        ScanArea(
            width = 0.9f,
            height = 0.35f
        )
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
                        scale = 6f,
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
                        text = SsidParser.parse(text),
                        exists = exists,
                        scanArea = scanArea,
                    )
                }
            }
        }
        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(220.dp)
                .padding(8.dp)
                .background(Color.White),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    8.dp,
                    Alignment.CenterHorizontally
                ),
                verticalAlignment = Alignment.CenterVertically

            ) {
                Button(
                    onClick = onScanToggle,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                    Text(if (enable) "STOP" else "SCAN")
                }
                OutlinedButton(
                    onClick = onFlashToggle,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                    Text("FLASH")
                }
            }
            Text("Result", style = MaterialTheme.typography.titleMedium)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(text = text.ifBlank { "No text found" })
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

    val bgColor = Color(0xFFE6F4EA)
    val textColor = Color(0xFF14532D)
    val iconColor = if (exists) Color(0xFFC66A12) else Color(0xFF16803C)

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val top = maxHeight * (1f - scanArea.height) / 2f

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = top - 46.dp)
                .background(bgColor, RoundedCornerShape(8.dp))
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
                color = textColor,
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
            flash = true,
            enable = true,
            hasPermission = false,
            onResult = {},
            onScanToggle = {},
            onFlashToggle = {},
            onRequestPermission = {},
            isPreview = false
        )
    }
}
