package com.runit.ocrscanner

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import com.runit.ocrscanner.model.ScanResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class CsvWriter(context: Context) {
    private val context = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    fun share(
        rows: List<ScanResult>,
        onError: (Exception) -> Unit
    ) {
        if (rows.isEmpty()) return

        val snapshot = rows.toList()
        executor.execute {
            try {
                val file = write(snapshot)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileProvider",
                    file
                )

                mainHandler.post {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = ClipData.newUri(
                            context.contentResolver,
                            "SSID CSV",
                            uri
                        )
                    }

                    val chooser = Intent.createChooser(
                        intent,
                        "Xuất danh sách SSID"
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    try {
                        context.startActivity(chooser)
                    } catch (e: Exception) {
                        onError(e)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post { onError(e) }
            }
        }
    }

    private fun write(rows: List<ScanResult>): File {
        val dir = File(context.cacheDir, "exports").apply {
            check(exists() || mkdirs()) {
                "Cannot create CSV directory"
            }
        }

        val file = File(
            dir,
            "ssid_${System.currentTimeMillis()}.csv"
        )
        /*
        val dateFormat = SimpleDateFormat(
            "dd/MM/yyyy HH:mm:ss",
            Locale.getDefault()
        )
        */
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            rows.forEach { row ->

                writer.appendLine(
                    escape(row.code)
                )
            }
        }

        return file
    }

    private fun escape(value: String): String =
        "\"${value.replace("\"", "\"\"")}\""

    fun close() {
        executor.shutdown()
    }
}