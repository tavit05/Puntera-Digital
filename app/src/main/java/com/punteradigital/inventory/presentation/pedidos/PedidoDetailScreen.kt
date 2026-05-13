package com.punteradigital.inventory.presentation.pedidos

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.punteradigital.inventory.presentation.components.ButtonType
import com.punteradigital.inventory.presentation.components.KineticButton
import com.punteradigital.inventory.presentation.components.KineticCard
import com.punteradigital.inventory.presentation.components.KineticTextField
import com.punteradigital.inventory.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

sealed class ScanResult {
    data class Success(val model: String, val size: String, val scanned: Int, val total: Int, val user: String) : ScanResult()
    data class WrongSize(val scannedSize: String, val expected: String) : ScanResult()
    data class WrongModel(val scannedModel: String, val expected: String) : ScanResult()
    data class AlreadyComplete(val model: String, val size: String, val total: Int) : ScanResult()
    object Empty : ScanResult()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PedidoDetailScreen(
    pedidoIndex: Int,
    onBack: () -> Unit,
    currentUserName: String = "José García"
) {
    val pedidos = PedidoRepository.pedidos
    if (pedidoIndex !in pedidos.indices) {
        onBack()
        return
    }

    val pedido = pedidos[pedidoIndex]
    var scanInput by remember { mutableStateOf("") }
    var scanResult by remember { mutableStateOf<ScanResult>(ScanResult.Empty) }

    // Compute totals reactively
    val totalQty = pedido.lines.sumOf { it.qty }
    val totalScanned = pedido.lines.sumOf { it.scanned }
    val isComplete = totalScanned >= totalQty
    val overallPct = if (totalQty > 0) (totalScanned.toFloat() / totalQty * 100).toInt() else 0

    val statusColor = when (pedido.status) {
        "pendiente" -> StandByAmber
        "en-proceso" -> RefillBlue
        "completo" -> DispatchGreen
        "entregado" -> Color(0xFF009688)
        "retornado" -> CriticalRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pedido ${pedido.id}", fontWeight = FontWeight.Bold)
                        Text(
                            pedido.client,
                            style = MaterialTheme.typography.bodySmall,
                            color = DispatchGreen
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status + Creator header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = statusColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            pedido.status.replace("-", " ").uppercase(),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = statusColor
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("👤 ", fontSize = 14.sp)
                        Text(pedido.creator, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Overall progress
            item {
                KineticCard(padding = 16.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Progreso General", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("$totalScanned/$totalQty pares", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DispatchGreen)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { overallPct / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = if (isComplete) DispatchGreen else StandByAmber,
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }

            // Lines Detail
            item {
                Text(
                    "Líneas del Pedido",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(pedido.lines) { line ->
                val pct = if (line.qty > 0) (line.scanned.toFloat() / line.qty * 100).toInt() else 0
                val lineColor = when {
                    line.scanned >= line.qty -> DispatchGreen
                    line.scanned > 0 -> StandByAmber
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${line.model} · T${line.size}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${line.scanned}/${line.qty}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = lineColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(6.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                                    RoundedCornerShape(3.dp)
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = pct / 100f)
                                    .background(lineColor, RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }
            }

            // Scan Input (hidden when complete)
            if (!isComplete && pedido.status !in listOf("entregado", "retornado")) {
                item {
                    Text(
                        "📷 Escanear Pares",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        KineticTextField(
                            value = scanInput,
                            onValueChange = { scanInput = it },
                            label = "UUID del par (ej: FS-2026A-42-001)",
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        FilledIconButton(
                            onClick = {
                                scanResult = processScan(scanInput, pedido, currentUserName)
                                scanInput = ""
                            },
                            modifier = Modifier.size(56.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("📷", fontSize = 24.sp)
                        }
                    }
                }
            }

            // Scan Result Banner
            if (scanResult !is ScanResult.Empty) {
                item {
                    ScanResultBanner(scanResult)
                }
            }

            // Completion area
            if (isComplete && pedido.status !in listOf("entregado", "retornado")) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = DispatchGreen.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DispatchGreen.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🎉", fontSize = 40.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("¡Pedido Completo!", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DispatchGreen)
                            Text(
                                "Todos los pares han sido escaneados correctamente.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            KineticButton(
                                text = "🚚 CONFIRMAR ENTREGA",
                                onClick = {
                                    pedido.status = "entregado"
                                    // Trigger recomposition
                                    pedidos[pedidoIndex] = pedido.copy(status = "entregado")
                                },
                                type = ButtonType.PRIMARY
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            KineticButton(
                                text = "↩ REGISTRAR RETORNO",
                                onClick = {
                                    pedido.status = "retornado"
                                    pedidos[pedidoIndex] = pedido.copy(status = "retornado")
                                },
                                type = ButtonType.DANGER
                            )
                        }
                    }
                }
            }

            // Delivered/Returned status
            if (pedido.status == "entregado") {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF009688).copy(alpha = 0.08f)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🚚", fontSize = 40.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Pedido Entregado", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF009688))
                            Text("Entrega confirmada por $currentUserName", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (pedido.status == "retornado") {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = CriticalRed.copy(alpha = 0.08f)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("↩", fontSize = 40.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Pedido Retornado", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = CriticalRed)
                            Text("Retorno registrado por $currentUserName", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Scanned pairs list
            item {
                Text(
                    "Pares Escaneados (${pedido.scannedPairs.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(pedido.scannedPairs) { sp ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DispatchGreen.copy(alpha = 0.06f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = DispatchGreen.copy(alpha = 0.2f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("✓", color = DispatchGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            sp.uuid,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(sp.time, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("👤 ${sp.user}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

// ═══ SCAN VALIDATION ENGINE ═══

// Cached formatter — avoids allocating a new SimpleDateFormat on every scan
private val scanTimeFormat by lazy { SimpleDateFormat("HH:mm", Locale.getDefault()) }

private fun processScan(uuid: String, pedido: Pedido, currentUserName: String): ScanResult {
    if (uuid.isBlank()) return ScanResult.Empty

    val parts = uuid.split("-")
    var scannedModel = ""
    var scannedSize = ""

    // Parse UUID format: PREFIX-LOTE-TALLA-SEQ
    if (parts.size >= 3) {
        scannedSize = parts[2]
        scannedModel = when {
            uuid.startsWith("FS-2026A") -> "FS300CMFFFPBL"
            uuid.startsWith("FS-2026B") -> "FS302CMN"
            uuid.startsWith("SF-") -> "SF200LT"
            else -> "FS400BK"
        }
    }

    // Also accept manual format: MODEL T## (e.g., "FS300CMFFFPBL T42")
    if (scannedModel.isEmpty() && uuid.contains("T", ignoreCase = true)) {
        val regex = Regex("""^(\w+)\s*T(\d+)""", RegexOption.IGNORE_CASE)
        regex.find(uuid)?.let { match ->
            scannedModel = match.groupValues[1]
            scannedSize = match.groupValues[2]
        }
    }

    // Find matching line
    val matchingLine = pedido.lines.find { it.model == scannedModel && it.size == scannedSize }

    if (matchingLine == null) {
        // Check if model exists but wrong size
        val modelExists = pedido.lines.find { it.model == scannedModel }
        return if (modelExists != null) {
            val expected = pedido.lines.filter { it.model == scannedModel }.joinToString(", ") { "T${it.size}" }
            ScanResult.WrongSize(scannedSize, expected)
        } else {
            val expected = pedido.lines.map { it.model }.distinct().joinToString(", ")
            ScanResult.WrongModel(scannedModel.ifEmpty { "desconocido" }, expected)
        }
    }

    // Check quantity
    if (matchingLine.scanned >= matchingLine.qty) {
        return ScanResult.AlreadyComplete(matchingLine.model, matchingLine.size, matchingLine.qty)
    }

    // SUCCESS — update state
    matchingLine.scanned++
    val timeStr = scanTimeFormat.format(Date())
    pedido.scannedPairs.add(0, ScannedPair(uuid, timeStr, currentUserName))

    if (pedido.status == "pendiente") {
        pedido.status = "en-proceso"
    }

    // Check if all complete
    val totalQty = pedido.lines.sumOf { it.qty }
    val totalSc = pedido.lines.sumOf { it.scanned }
    if (totalSc >= totalQty) {
        pedido.status = "completo"
    }

    return ScanResult.Success(scannedModel, scannedSize, matchingLine.scanned, matchingLine.qty, currentUserName)
}

@Composable
private fun ScanResultBanner(result: ScanResult) {
    val (bgColor, iconText, title, detail) = when (result) {
        is ScanResult.Success -> Quadruple(
            DispatchGreen,
            "✅",
            "¡Correcto! ${result.model} T${result.size}",
            "${result.scanned}/${result.total} pares · Escaneado por ${result.user}"
        )
        is ScanResult.WrongSize -> Quadruple(
            CriticalRed,
            "❌",
            "Talla ${result.scannedSize} no coincide",
            "Se esperaba: ${result.expected}"
        )
        is ScanResult.WrongModel -> Quadruple(
            CriticalRed,
            "❌",
            "Modelo ${result.scannedModel} NO corresponde",
            "Este pedido requiere: ${result.expected}"
        )
        is ScanResult.AlreadyComplete -> Quadruple(
            StandByAmber,
            "⚠️",
            "Cantidad completa para T${result.size}",
            "Ya se escanearon ${result.total}/${result.total} pares de ${result.model}"
        )
        ScanResult.Empty -> return
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = bgColor.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, bgColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(iconText, fontSize = 28.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = bgColor)
                Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
