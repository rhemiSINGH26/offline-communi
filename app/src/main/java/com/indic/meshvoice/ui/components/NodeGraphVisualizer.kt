package com.indic.meshvoice.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indic.meshvoice.mesh.PeerScanStatus
import com.indic.meshvoice.model.MeshNode
import com.indic.meshvoice.ui.theme.*
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun NodeGraphVisualizer(
    myNodeId: String,
    connectedNodes: List<MeshNode>,
    scanStatus: PeerScanStatus = PeerScanStatus.SEARCHING,
    onRetryScan: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 145.dp, max = 195.dp)
            .height(175.dp)
            .background(DarkCard, RoundedCornerShape(14.dp))
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f + 6.dp.toPx())
            val centerRadius = 22.dp.toPx()

            // Draw Central (My) Node
            drawCircle(
                color = CyberCyan.copy(alpha = 0.25f),
                radius = centerRadius + 8.dp.toPx(),
                center = center
            )
            drawCircle(
                color = CyberCyan,
                radius = centerRadius,
                center = center
            )

            val myLabel = "YOU\n(${myNodeId.take(4)})"
            drawText(
                textMeasurer = textMeasurer,
                text = myLabel,
                topLeft = Offset(center.x - 16.dp.toPx(), center.y - 11.dp.toPx()),
                style = TextStyle(color = DarkBg, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            )

            // Draw connected peer nodes around in a circle
            if (connectedNodes.isNotEmpty()) {
                val minDimension = min(size.width, size.height)
                val orbitRadius = (minDimension / 2f) - 24.dp.toPx()
                val angleStep = (2 * Math.PI / connectedNodes.size)

                connectedNodes.forEachIndexed { index, node ->
                    val angle = index * angleStep
                    val nodeX = center.x + (orbitRadius * cos(angle)).toFloat()
                    val nodeY = center.y + (orbitRadius * sin(angle)).toFloat()
                    val nodeCenter = Offset(nodeX, nodeY)

                    // Draw connection line
                    drawLine(
                        color = NeonEmerald.copy(alpha = 0.7f),
                        start = center,
                        end = nodeCenter,
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    )

                    // Draw peer node circle
                    drawCircle(
                        color = NeonEmerald.copy(alpha = 0.35f),
                        radius = 16.dp.toPx(),
                        center = nodeCenter
                    )
                    drawCircle(
                        color = NeonEmerald,
                        radius = 11.dp.toPx(),
                        center = nodeCenter
                    )

                    // Node text
                    val peerLabel = node.name.take(5)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = peerLabel,
                        topLeft = Offset(nodeX - 14.dp.toPx(), nodeY + 11.dp.toPx()),
                        style = TextStyle(color = TextSecondary, fontSize = 8.sp)
                    )
                }
            } else {
                // Pulse scanning ring if searching for peers
                drawCircle(
                    color = CyberCyan.copy(alpha = 0.12f),
                    radius = centerRadius + 24.dp.toPx(),
                    center = center
                )
            }
        }

        // Live Header Overlay: Status Badge & Retry Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when (scanStatus) {
                                PeerScanStatus.CONNECTED -> NeonEmerald.copy(alpha = 0.2f)
                                PeerScanStatus.CONNECTING -> SaffronOrange.copy(alpha = 0.2f)
                                else -> CyberCyan.copy(alpha = 0.2f)
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = scanStatus.badge,
                        color = when (scanStatus) {
                            PeerScanStatus.CONNECTED -> NeonEmerald
                            PeerScanStatus.CONNECTING -> SaffronOrange
                            else -> CyberCyan
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.width(6.dp))

                Text(
                    text = if (connectedNodes.isNotEmpty()) "${connectedNodes.size} Peer(s) in Mesh" else "Seeking Relays...",
                    color = TextMuted,
                    fontSize = 9.sp
                )
            }

            // Quick Re-Scan Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkBorder)
                    .clickable { onRetryScan() }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry Mesh Scan",
                        tint = CyberCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "RETRY",
                        color = CyberCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
