package com.abrah.nightmare.coloring

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.abrah.nightmare.HarnessOps
import com.abrah.nightmare.BackendState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Isolated entry point. Uses Nightmare's original graph runner, not a second AI runtime. */
class ColoringStudioActivity : ComponentActivity() {
    private data class Item(val path: String, val label: String, val status: String = "Queued", val result: String? = null)
    private val entries = mutableStateListOf<Item>()
    private var running by mutableStateOf(false)
    private var stopRequested = false
    private var message by mutableStateOf("")
    private var strength by mutableFloatStateOf(0.55f)
    private val ops by lazy { HarnessOps(applicationContext, object : HarnessOps.Sink {
        override fun say(text: String, bad: Boolean) { android.util.Log.i("ColoringStudio", text) }
        override fun backend(state: BackendState) {}
    }) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restore()
        setContent {
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
                if (!running) importSources(uris)
            }
            MaterialTheme {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Coloring Studio", style = MaterialTheme.typography.headlineMedium)
                    Text("AI coloring pages • one source per result")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { picker.launch("image/*") }, enabled = !running) { Text("Add photos") }
                        Button(onClick = { start() }, enabled = !running && entries.any { it.status != "Completed" }) { Text("Generate") }
                        OutlinedButton(onClick = { stopRequested = true }, enabled = running) { Text("Stop after current") }
                    }
                    Text("Transformation strength: ${"%.2f".format(strength)}")
                    Slider(value = strength, onValueChange = { strength = it }, valueRange = 0.1f..0.95f, enabled = !running)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { exportPdf() }, enabled = !running && entries.any { it.result != null }) { Text("Export PDF") }
                        OutlinedButton(onClick = { entries.clear(); persist() }, enabled = !running) { Text("Clear queue") }
                    }
                    if (message.isNotEmpty()) Text(message)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(entries) { index, item ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(item.label)
                                    Text(item.status)
                                    if (item.result != null) Text("Saved: ${item.result}", style = MaterialTheme.typography.bodySmall)
                                    if (!running && item.status.startsWith("Failed")) {
                                        TextButton(onClick = { entries[index] = item.copy(status = "Queued"); persist() }) { Text("Retry") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun importSources(uris: List<Uri>) {
        val folder = File(filesDir, "coloring/sources").apply { mkdirs() }
        uris.forEach { uri ->
            try {
                val file = File(folder, "${UUID.randomUUID()}.img")
                contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                    ?: error("Unable to read image")
                require(file.length() > 0L) { "Image is empty" }
                entries.add(Item(file.absolutePath, uri.lastPathSegment ?: file.name))
            } catch (e: Exception) { message = "Import failed: ${e.message}" }
        }
        persist()
    }

    private fun start() {
        if (running) return
        running = true
        stopRequested = false
        val selectedStrength = strength
        lifecycleScope.launch {
            try {
                for (index in entries.indices) {
                    if (stopRequested) break
                    val item = entries[index]
                    if (item.status == "Completed" && item.result != null) continue
                    entries[index] = item.copy(status = "Processing")
                    persist()
                    try {
                        val result = withContext(Dispatchers.IO) { ops.runColoringPrototype(item.path, selectedStrength) }
                        entries[index] = item.copy(status = "Completed", result = result)
                    } catch (e: Exception) {
                        entries[index] = item.copy(status = "Failed: ${e.message?.take(160)}")
                    }
                    persist()
                }
            } finally { running = false; persist() }
        }
    }

    private fun persist() {
        val file = File(filesDir, "coloring/queue.tsv")
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "queue.tsv.tmp")
        temporary.writeText(entries.joinToString("\n") {
            listOf(it.path, it.label, it.status, it.result ?: "").joinToString("\t") { v -> v.replace('\t', ' ').replace('\n', ' ') }
        })
        if (!temporary.renameTo(file)) {
            file.writeText(temporary.readText())
            temporary.delete()
        }
    }

    private fun restore() {
        val file = File(filesDir, "coloring/queue.tsv")
        if (!file.exists()) return
        file.readLines().forEach { line ->
            val c = line.split('\t')
            if (c.size >= 4 && File(c[0]).exists()) entries.add(Item(c[0], c[1], if (c[2] == "Processing") "Queued" else c[2], c[3].ifEmpty { null }))
        }
    }

    private fun exportPdf() {
        val outputs = entries.mapNotNull { it.result }
        if (outputs.isEmpty()) return
        lifecycleScope.launch {
            try {
                val pdfFile = withContext(Dispatchers.IO) {
                    val pdf = PdfDocument()
                    try {
                        outputs.forEachIndexed { index, uri ->
                            val bmp = contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
                                ?: error("Cannot decode result $index")
                            try {
                                val info = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
                                val page = pdf.startPage(info)
                                val scale = minOf(555f / bmp.width, 802f / bmp.height)
                                val w = bmp.width * scale; val h = bmp.height * scale
                                page.canvas.drawColor(Color.WHITE)
                                page.canvas.drawBitmap(bmp, null, android.graphics.RectF((595-w)/2, (842-h)/2, (595+w)/2, (842+h)/2), null)
                                pdf.finishPage(page)
                            } finally { bmp.recycle() }
                        }
                        File(cacheDir, "share/coloring_${System.currentTimeMillis()}.pdf").also { it.parentFile?.mkdirs() }.also { file -> file.outputStream().use { pdf.writeTo(it) } }
                    } finally { pdf.close() }
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(this@ColoringStudioActivity, "$packageName.share", pdfFile)
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Export coloring book"))
            } catch (e: Exception) { message = "PDF export failed: ${e.message}" }
        }
    }
}
