package com.punteradigital.inventory.presentation.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.punteradigital.inventory.R
import com.punteradigital.inventory.presentation.components.ButtonType
import com.punteradigital.inventory.presentation.components.KineticButton
import com.punteradigital.inventory.presentation.components.KineticCard
import com.punteradigital.inventory.presentation.components.KineticTextField
import com.punteradigital.inventory.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogEditScreen(
    itemId: String?,
    onBack: () -> Unit
) {
    val item = remember { mockCatalog.find { it.id == itemId } }
    val isNew = item == null

    var sku by remember { mutableStateOf(item?.name ?: "") }
    var nombreComercial by remember { mutableStateOf(item?.sku ?: "") }
    var isActive by remember { mutableStateOf(item?.isActive ?: true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (isNew) "Nuevo Modelo" else "Editar Modelo", fontWeight = FontWeight.Bold)
                        if (!isNew) {
                            Text(
                                sku,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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

            // Image Zone
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (item != null) {
                        Image(
                            painter = painterResource(id = item.imageResId),
                            contentDescription = item.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    }
                    
                    // Overlay tint
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                    )
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📸", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Cambiar foto",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Info Card
            KineticCard(padding = 20.dp) {
                Text(
                    "Información del Modelo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                KineticTextField(
                    value = sku,
                    onValueChange = { sku = it },
                    label = "Código SKU",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                KineticTextField(
                    value = nombreComercial,
                    onValueChange = { nombreComercial = it },
                    label = "Nombre Comercial",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    KineticTextField(
                        value = "36",
                        onValueChange = { },
                        label = "Talla Mín",
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    KineticTextField(
                        value = "46",
                        onValueChange = { },
                        label = "Talla Máx",
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                KineticTextField(
                    value = "8",
                    onValueChange = { },
                    label = "Pares por Caja Master",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // State toggle
            KineticCard(padding = 16.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Modelo Activo", fontWeight = FontWeight.Bold)
                        Text("Visible en entrada y despacho", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = KineticPrimary)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            KineticButton(
                text = "GUARDAR CAMBIOS",
                onClick = { onBack() },
                type = ButtonType.PRIMARY
            )
            
            if (!isNew) {
                Spacer(modifier = Modifier.height(8.dp))
                KineticButton(
                    text = "ELIMINAR MODELO",
                    onClick = { onBack() },
                    type = ButtonType.DANGER
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
