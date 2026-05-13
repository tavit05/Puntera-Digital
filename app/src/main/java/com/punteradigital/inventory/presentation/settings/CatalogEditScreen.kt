package com.punteradigital.inventory.presentation.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
    var tallaMin by remember { mutableStateOf("36") }
    var tallaMax by remember { mutableStateOf("46") }
    var paresPorCaja by remember { mutableStateOf("8") }
    var isActive by remember { mutableStateOf(item?.isActive ?: true) }

    // Image state
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var showImagePicker by remember { mutableStateOf(false) }

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { imageUri = it }
    }

    // Camera picker
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        // For simplicity, camera returns a thumbnail. In production, use FileProvider + TakePicture
        // bitmap is available for display but for now just mark that camera was used
        if (bitmap != null) {
            // In production: save bitmap to internal storage and use URI
            // For now, this triggers the visual feedback that camera was used
        }
    }

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

            // Image Zone — clickable to change photo
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showImagePicker = true },
                shape = RoundedCornerShape(16.dp),
                color = Color.White
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (imageUri != null) {
                        // Show selected image
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Foto del producto",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (item != null) {
                        // Show default resource image
                        Image(
                            painter = painterResource(id = item.imageResId),
                            contentDescription = item.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    }
                    
                    // Overlay tint
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f))
                    )
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (imageUri != null) "Foto Actualizada ✓" else "📸 Tocar para cambiar foto",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
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
                        value = tallaMin,
                        onValueChange = { tallaMin = it },
                        label = "Talla Mín",
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    KineticTextField(
                        value = tallaMax,
                        onValueChange = { tallaMax = it },
                        label = "Talla Máx",
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                KineticTextField(
                    value = paresPorCaja,
                    onValueChange = { paresPorCaja = it },
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

    // Image picker bottom sheet
    if (showImagePicker) {
        AlertDialog(
            onDismissRequest = { showImagePicker = false },
            icon = {
                Icon(
                    Icons.Default.Photo,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = { Text("Cambiar Foto del Producto", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Camera option
                    OutlinedButton(
                        onClick = {
                            showImagePicker = false
                            cameraLauncher.launch(null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text("📷 Tomar Foto", fontWeight = FontWeight.Bold)
                    }

                    // Gallery option
                    OutlinedButton(
                        onClick = {
                            showImagePicker = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null)
                        Spacer(Modifier.width(8.dp))
                        Text("🖼 Elegir de Galería", fontWeight = FontWeight.Bold)
                    }

                    // Remove option
                    if (imageUri != null) {
                        OutlinedButton(
                            onClick = {
                                showImagePicker = false
                                imageUri = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CriticalRed)
                        ) {
                            Icon(Icons.Default.Delete, null)
                            Spacer(Modifier.width(8.dp))
                            Text("🗑 Eliminar Foto", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImagePicker = false }) {
                    Text("CANCELAR")
                }
            }
        )
    }
}
