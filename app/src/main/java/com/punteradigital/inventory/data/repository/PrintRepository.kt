package com.punteradigital.inventory.data.repository

import android.util.Log
import com.punteradigital.inventory.data.local.PrinterPreferences
import com.punteradigital.inventory.data.remote.PrintCsvBuilder
import com.punteradigital.inventory.data.remote.PrintLabelItem
import com.punteradigital.inventory.data.remote.PrintService
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for printing labels via BarTender.
 * Handles retry logic with exponential backoff, error categorization,
 * and offline queue fallback.
 */
@Singleton
class PrintRepository @Inject constructor(
    private val printerPreferences: PrinterPreferences,
    private val syncManager: SyncManager
) {
    private val TAG = "PrintRepository"

    companion object {
        private const val MAX_RETRIES = 3
        private const val INITIAL_DELAY_MS = 500L
        private const val BACKOFF_MULTIPLIER = 2.0
    }

    // Cached client — avoids rebuilding TCP connection pools on every print call.
    // Invalidated when printer IP/port configuration changes.
    private var cachedBaseUrl: String? = null
    private var cachedPrintService: PrintService? = null
    private var cachedOkHttpClient: OkHttpClient? = null

    /**
     * Result of a print operation.
     */
    sealed class PrintResult {
        data class Success(val labelCount: Int) : PrintResult()
        data class Queued(val message: String) : PrintResult()
        data class Error(val error: PrintError) : PrintResult()
    }

    /**
     * Categorized errors for user-friendly feedback.
     */
    sealed class PrintError(val userMessage: String, val technicalMessage: String) {
        class NotConfigured : PrintError(
            "Impresora no configurada",
            "PrinterPreferences.isConfigured = false"
        )
        class NetworkUnreachable(ip: String) : PrintError(
            "No se puede conectar al servidor ($ip)",
            "Network unreachable or host unknown"
        )
        class ConnectionTimeout(ip: String) : PrintError(
            "Tiempo de espera agotado conectando a $ip",
            "Socket timeout exceeded"
        )
        class ServerError(code: Int) : PrintError(
            "BarTender respondió con error (código $code)",
            "HTTP $code response from BarTender"
        )
        class InvalidData(detail: String) : PrintError(
            "Datos de etiqueta inválidos: $detail",
            "CSV build validation failed: $detail"
        )
        class Unknown(cause: Throwable) : PrintError(
            "Error inesperado al imprimir",
            cause.message ?: "Unknown error"
        )
    }

    /**
     * Print labels with automatic retry and offline queue fallback.
     *
     * Flow:
     * 1. Validate config & data
     * 2. Attempt direct print (with retries if enabled)
     * 3. On failure, queue for later sync if offline queue is enabled
     * 4. Return user-friendly result
     */
    suspend fun printLabels(items: List<PrintLabelItem>): PrintResult {
        // Validation
        if (!printerPreferences.isConfigured) {
            return PrintResult.Error(PrintError.NotConfigured())
        }
        if (items.isEmpty()) {
            return PrintResult.Error(PrintError.InvalidData("Lista de etiquetas vacía"))
        }

        val printService = buildPrintService()
        val maxAttempts = if (printerPreferences.retryEnabled) MAX_RETRIES else 1
        var successCount = 0
        var lastException: Exception? = null

        // Iterate and send one POST request per label mapping to BarTender's JSON variables
        for (item in items) {
            val json = try { PrintCsvBuilder.buildJsonPayload(item) } catch (e: Exception) { continue }
            val body = json.toRequestBody("application/json".toMediaType())
            var itemSuccess = false

            for (attempt in 1..maxAttempts) {
                try {
                    val response = printService.printLabels(body)
                    if (response.isSuccessful) {
                        itemSuccess = true
                        successCount++
                        break
                    } else {
                        if (response.code() in 400..499) {
                            lastException = RuntimeException("HTTP ${response.code()}")
                            break
                        }
                        lastException = RuntimeException("HTTP ${response.code()}")
                    }
                } catch (e: Exception) {
                    lastException = e
                    if (attempt < maxAttempts) {
                        val delayMs = (INITIAL_DELAY_MS * Math.pow(BACKOFF_MULTIPLIER, (attempt - 1).toDouble())).toLong() // Backoff wait
                        delay(delayMs)
                    }
                }
            }

            // Stop if an item completely fails (network down), we queue the rest
            if (!itemSuccess) {
                Log.e(TAG, "Failed to print item ${item.uuid}, stopping batch")
                break
            }
        }

        if (successCount == items.size) {
            Log.i(TAG, "✅ Print batch successful: $successCount labels")
            return PrintResult.Success(successCount)
        } else {
            val failedItems = items.drop(successCount)
            if (printerPreferences.offlineQueueEnabled && failedItems.isNotEmpty()) {
                try {
                    syncManager.enqueuePrintJob(failedItems)
                    return PrintResult.Queued("Impresas $successCount. ${failedItems.size} guardadas en cola por error.")
                } catch (e: Exception) {
                    return PrintResult.Error(categorizePrintError(lastException ?: e))
                }
            }
            return PrintResult.Error(categorizePrintError(lastException ?: RuntimeException("Batch failure")))
        }
    }

    /**
     * Test connection to the BarTender server.
     * Sends a simple HEAD/OPTIONS request to verify reachability.
     */
    suspend fun testConnection(): PrintResult {
        if (!printerPreferences.isConfigured) {
            return PrintResult.Error(PrintError.NotConfigured())
        }

        return try {
            val testCsv = """{"UUID":"TEST-PING"}"""
            val body = testCsv.toRequestBody("application/json".toMediaType())
            val printService = buildPrintService()
            val response = printService.printLabels(body)

            if (response.isSuccessful) {
                PrintResult.Success(0)
            } else {
                PrintResult.Error(PrintError.ServerError(response.code()))
            }
        } catch (e: Exception) {
            PrintResult.Error(categorizePrintError(e))
        }
    }

    /**
     * Builds or returns a cached PrintService.
     * Only rebuilds OkHttpClient+Retrofit when IP/port/timeout changes.
     * This allows TCP connection reuse (keep-alive) across multiple print jobs.
     */
    private fun buildPrintService(): PrintService {
        val currentBaseUrl = printerPreferences.getBaseUrl()
        val currentTimeout = printerPreferences.timeoutSeconds

        // Return cached instance if config hasn't changed
        val existing = cachedPrintService
        if (existing != null && cachedBaseUrl == currentBaseUrl) {
            return existing
        }

        // Config changed or first call — build new client
        Log.d(TAG, "Building new PrintService for $currentBaseUrl")
        val client = OkHttpClient.Builder()
            .connectTimeout(currentTimeout.toLong(), TimeUnit.SECONDS)
            .readTimeout(currentTimeout.toLong(), TimeUnit.SECONDS)
            .writeTimeout(currentTimeout.toLong(), TimeUnit.SECONDS)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(currentBaseUrl)
            .client(client)
            .build()
        val service = retrofit.create(PrintService::class.java)

        cachedOkHttpClient = client
        cachedPrintService = service
        cachedBaseUrl = currentBaseUrl
        return service
    }

    /**
     * Maps low-level exceptions to user-friendly PrintError categories.
     */
    private fun categorizePrintError(e: Exception): PrintError {
        val ip = printerPreferences.serverIp
        return when (e) {
            is UnknownHostException -> PrintError.NetworkUnreachable(ip)
            is ConnectException -> PrintError.NetworkUnreachable(ip)
            is SocketTimeoutException -> PrintError.ConnectionTimeout(ip)
            else -> PrintError.Unknown(e)
        }
    }
}
