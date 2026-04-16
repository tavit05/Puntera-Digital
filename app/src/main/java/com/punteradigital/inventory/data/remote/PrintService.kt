package com.punteradigital.inventory.data.remote

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * BarTender Integration Builder endpoint.
 * Sends CSV payload via POST to http://[IP]:8080/imprimir
 * 
 * CSV columns: UUID,Modelo,Talla,Lote,Origen
 */
interface PrintService {

    @Headers("Content-Type: application/json")
    @POST("Integration/PunteraDigital_QR/Execute")
    suspend fun printLabels(@Body jsonBody: RequestBody): Response<Unit>
}

/**
 * Helper to build the CSV payload for BarTender.
 */
object PrintCsvBuilder {
    fun buildJsonPayload(item: PrintLabelItem): String {
        // Sending as JSON object for BarTender "Variables JSON" input format
        return """{"UUID":"${item.uuid}","Modelo":"${item.model}","Talla":"${item.size}","Lote":"${item.lot}","Origen":"${item.origin}"}"""
    }
}

data class PrintLabelItem(
    val uuid: String,
    val model: String,
    val size: String,
    val lot: String,
    val origin: String
)
