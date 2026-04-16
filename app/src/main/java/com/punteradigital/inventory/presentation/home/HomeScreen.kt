package com.punteradigital.inventory.presentation.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.punteradigital.inventory.R
import com.punteradigital.inventory.presentation.traceability.TimelineMovementItem
import com.punteradigital.inventory.presentation.viewmodel.InventoryViewModel
import com.punteradigital.inventory.presentation.components.*
import com.punteradigital.inventory.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: InventoryViewModel,
    onNavigateToEntry: () -> Unit,
    onNavigateToMovements: () -> Unit,
    onNavigateToRefill: () -> Unit
) {
    val user by viewModel.currentUser.collectAsState()
    val origin by viewModel.currentOrigin.collectAsState()
    val batchStatus by viewModel.inventoryStatus.collectAsState(initial = emptyList())
    val movements by viewModel.traceabilityMovements.collectAsState(initial = emptyList())
    val totalAvailable by viewModel.totalAvailable.collectAsState(initial = 0)
    val totalStandBy by viewModel.totalStandBy.collectAsState(initial = 0)
    val totalMasterBoxes by viewModel.totalMasterBoxes.collectAsState(initial = 0)
    val pendingSync by viewModel.pendingSyncCount.collectAsState(initial = 0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header with user info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Hola, ${user?.name ?: "Operador"} \uD83D\uDC4B",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Modo: ${origin.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Sync indicator
            if (pendingSync > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StandByAmber.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.SyncProblem, null, tint = StandByAmber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("$pendingSync", style = MaterialTheme.typography.labelSmall, color = StandByAmber)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Product Hero Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
        ) {
            Image(
                painter = painterResource(id = R.drawable.boot_black),
                contentDescription = "Producto Destacado",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.8f),
                                Color.Black.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("Selección Activa", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("FS300CMFFPBL", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = SpaceGrotesk)
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = KineticSuccess.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "LOTE ACTIVO: 2026A",
                        color = KineticSuccess,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Counter Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EmojiCounterCard("📦", "Stock", "$totalAvailable", DispatchGreen, Modifier.weight(1f))
            EmojiCounterCard("⏸", "Stand-By", "$totalStandBy", StandByAmber, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EmojiCounterCard("📦", "Cajas Master", "$totalMasterBoxes", QualityPurple, Modifier.weight(1f))
            EmojiCounterCard("🏷", "Modelos", "${batchStatus.map { it.model }.distinct().size}", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Quick Actions (3 buttons)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KineticButton(
                text = "➕ Entrada",
                onClick = onNavigateToEntry,
                modifier = Modifier.weight(1f),
                type = ButtonType.PRIMARY
            )
            KineticButton(
                text = "🔄 Movim.",
                onClick = onNavigateToMovements,
                modifier = Modifier.weight(1f),
                type = ButtonType.SECONDARY
            )
            KineticButton(
                text = "📦 Rellenar",
                onClick = onNavigateToRefill,
                modifier = Modifier.weight(1f),
                type = ButtonType.SUCCESS
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Movimientos Recientes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (movements.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Sin movimientos recientes", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(movements.take(10)) { movement ->
                    TimelineMovementItem(movement)
                }
            }
        }
    }
}

@Composable
fun EmojiCounterCard(
    emoji: String,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    KineticCard(
        modifier = modifier,
        padding = 16.dp
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = color.copy(alpha = 0.15f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 18.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
