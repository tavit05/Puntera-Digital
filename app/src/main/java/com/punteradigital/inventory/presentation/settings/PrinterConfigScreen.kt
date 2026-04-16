package com.punteradigital.inventory.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.punteradigital.inventory.presentation.components.ButtonType
import com.punteradigital.inventory.presentation.components.KineticButton
import com.punteradigital.inventory.presentation.components.KineticCard
import com.punteradigital.inventory.presentation.components.KineticTextField
import com.punteradigital.inventory.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterConfigScreen(
    onBack: () -> Unit
) {
    var ipAddress by remember { mutableStateOf("192.168.0.50") }
    var port by remember { mutableStateOf("8080") }
    var resultText by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Impresora QR", fontWeight = FontWeight.Bold)
                        Text(
                            "BarTender Enterprise",
                            style = MaterialTheme.typography.bodySmall,
                            color = QualityPurple
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Status banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = CriticalRed.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CriticalRed.copy(alpha = 0.2f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, tint = CriticalRed, modifier = Modifier.padding(8.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Sin Configurar", fontWeight = FontWeight.Bold, color = CriticalRed)
                        Text("Ingrese IP y puerto del servidor", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Server Config Card
            KineticCard(padding = 20.dp) {
                Text(
                    "Servidor BarTender",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                KineticTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = "Dirección IP del Servidor *",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    KineticTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = "Puerto *",
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    KineticTextField(
                        value = "HTTP",
                        onValueChange = { },
                        label = "Protocolo",
                        modifier = Modifier.weight(1f),
                        readOnly = true
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                KineticTextField(
                    value = "/imprimir",
                    onValueChange = { },
                    label = "Endpoint",
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            KineticButton(
                text = if(isTesting) "PROBANDO..." else "PROBAR CONEXIÓN",
                onClick = { 
                    resultText = "Probando conexión HTTP a $ipAddress:$port..."
                    isTesting = true
                    // Simulate network
                    // In a real scenario, this updates shared preferences
                 },
                type = ButtonType.SECONDARY,
                icon = { Icon(Icons.Default.Print, null, tint = Color.White) }
            )

            if (resultText.isNotEmpty()) {
                Text(
                    text = resultText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}
