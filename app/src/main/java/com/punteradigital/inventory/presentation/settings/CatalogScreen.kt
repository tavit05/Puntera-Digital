package com.punteradigital.inventory.presentation.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.punteradigital.inventory.R
import com.punteradigital.inventory.presentation.components.ButtonType
import com.punteradigital.inventory.presentation.components.KineticButton
import com.punteradigital.inventory.ui.theme.KineticPrimary

data class MockCatalogItem(
    val id: String,
    val name: String,
    val sku: String,
    val imageResId: Int,
    val isActive: Boolean
)

val mockCatalog = listOf(
    MockCatalogItem("1", "FS300CMFFPBL", "Foot Safe 300 Comp", R.drawable.boot_black, true),
    MockCatalogItem("2", "FS302CMN", "Foot Safe 302", R.drawable.boot_brown, true),
    MockCatalogItem("3", "FS400BK", "Foot Safe 400 Black", R.drawable.boot_tactical, true),
    MockCatalogItem("4", "SF200LT", "Safety 200 Lite", R.drawable.shoe_lite, false)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    onBack: () -> Unit,
    onNavigateToEdit: (String?) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Catálogo de Modelos", fontWeight = FontWeight.Bold)
                        Text(
                            "${mockCatalog.size} modelos registrados",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                .padding(horizontal = 16.dp)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(mockCatalog) { item ->
                    CatalogItemCard(item) { onNavigateToEdit(item.id) }
                }
            }
        }
        
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            KineticButton(
                text = "AGREGAR MODELO",
                onClick = { onNavigateToEdit(null) },
                type = ButtonType.PRIMARY
            )
        }
    }
}

@Composable
fun CatalogItemCard(item: MockCatalogItem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.size(10.dp).align(Alignment.TopEnd),
                    shape = CircleShape,
                    color = if (item.isActive) Color(0xFF4CAF50) else Color.Red
                ) {}
            }
            
            Image(
                painter = painterResource(id = item.imageResId),
                contentDescription = item.name,
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(8.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = item.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.sku,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
