package com.schoolsync.teacher.ui.payslips

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.teacher.data.model.firestore.SalarySlipDoc
import com.schoolsync.teacher.ui.theme.Divider as DividerColor
import com.schoolsync.teacher.ui.theme.ErrorRed
import com.schoolsync.teacher.ui.theme.Glass
import com.schoolsync.teacher.ui.theme.GlassBorder
import com.schoolsync.teacher.ui.theme.GradientBackground
import com.schoolsync.teacher.ui.theme.SuccessGreen
import com.schoolsync.teacher.ui.theme.SuccessGreenSurface
import com.schoolsync.teacher.ui.theme.Teal
import com.schoolsync.teacher.ui.theme.TealSurface
import com.schoolsync.teacher.ui.theme.TextPrimary
import com.schoolsync.teacher.ui.theme.TextSecondary
import com.schoolsync.teacher.ui.theme.TextTertiary
import java.text.NumberFormat
import java.util.Locale

private fun fmtMoney(v: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale("en", "IN"))
    nf.minimumFractionDigits = 2
    nf.maximumFractionDigits = 2
    return "₹" + nf.format(v)
}

private val ACRONYMS = setOf("HRA", "DA", "TA", "TDS", "PF", "ESI")

private fun prettify(key: String): String {
    val words = key.replace(Regex("([a-z])([A-Z])"), "$1 $2").split(" ")
    return words.joinToString(" ") { w ->
        val upper = w.uppercase()
        if (upper in ACRONYMS) upper else w.replaceFirstChar { it.uppercase() }
    }
}

@Composable
@ReadOnlyComposable
private fun statusColor(status: String): Pair<Color, Color> = when (status.lowercase()) {
    "paid" -> SuccessGreen to SuccessGreenSurface
    "finalized" -> Teal to TealSurface
    else -> TextTertiary to Glass
}

@Composable
fun PayslipsScreen(viewModel: PayslipsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    GradientBackground {
        Scaffold(containerColor = Color.Transparent) { inner ->
            Column(Modifier.fillMaxSize().padding(inner)) {
                PayslipsTopBar(onRefresh = viewModel::load, isLoading = state.isLoading)

                if (state.isLoading && state.payslips.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Teal)
                    }
                } else if (state.error != null && state.payslips.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(state.error ?: "", color = ErrorRed, fontSize = 14.sp)
                    }
                } else if (state.payslips.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.payslips, key = { it.id.ifEmpty { it.monthKey + it.staffId } }) { slip ->
                            PayslipCard(
                                slip = slip,
                                expanded = state.expandedId == slip.id,
                                onToggle = { viewModel.toggleExpand(slip.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PayslipsTopBar(onRefresh: () -> Unit, isLoading: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.AccountBalanceWallet, null, tint = Teal, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(10.dp))
        Text("My Payslips", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onRefresh, enabled = !isLoading) {
            Icon(Icons.Filled.Refresh, "Refresh", tint = if (isLoading) TextTertiary else Teal)
        }
    }
}

@Composable
private fun EmptyState() {
    com.schoolsync.teacher.ui.components.EmptyStatePro(
        icon = Icons.Filled.AccountBalanceWallet,
        title = "No payslips yet",
        description = "Your monthly payslips will appear here once released by HR.",
    )
}

@Composable
private fun PayslipCard(slip: SalarySlipDoc, expanded: Boolean, onToggle: () -> Unit) {
    val (sColor, sBg) = statusColor(slip.status)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Glass)
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${slip.month.ifEmpty { slip.monthKey }} ${slip.year}".trim(),
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(fmtMoney(slip.netPayable), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Teal)
            }
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(sBg).padding(horizontal = 8.dp, vertical = 4.dp)
                ) { Text(slip.status, color = sColor, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
                Spacer(Modifier.height(4.dp))
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    null, tint = TextTertiary, modifier = Modifier.size(20.dp)
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = 12.dp)) {
                Divider(color = DividerColor)
                Spacer(Modifier.height(10.dp))
                InfoRow("Working days", "${slip.workingDays}")
                InfoRow("Present", "${slip.presentDays}")
                InfoRow("Absent", "${slip.daysAbsent}")
                if (slip.lwpDays > 0) InfoRow("LWP days", "${slip.lwpDays}")
                if (slip.paidLeaveDays > 0) InfoRow("Paid leave", "${slip.paidLeaveDays}")
                Spacer(Modifier.height(10.dp))
                SectionHeader("Earnings")
                slip.earnings.forEach { (k, v) -> if (v != 0.0) InfoRow(prettify(k), fmtMoney(v)) }
                InfoRow("Gross", fmtMoney(slip.grossEarnings), bold = true)
                Spacer(Modifier.height(10.dp))
                SectionHeader("Deductions")
                slip.deductions.forEach { (k, v) -> if (v != 0.0) InfoRow(prettify(k), fmtMoney(v)) }
                InfoRow("Total deductions", fmtMoney(slip.totalDeductions), bold = true)
                Spacer(Modifier.height(10.dp))
                Divider(color = DividerColor)
                Spacer(Modifier.height(10.dp))
                InfoRow("Net payable", fmtMoney(slip.netPayable), bold = true, emphasis = true)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        color = Teal,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = androidx.compose.ui.unit.TextUnit(0.8f, androidx.compose.ui.unit.TextUnitType.Sp),
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun InfoRow(label: String, value: String, bold: Boolean = false, emphasis: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(
            value,
            color = if (emphasis) Teal else TextPrimary,
            fontSize = if (emphasis) 15.sp else 13.sp,
            fontWeight = if (bold || emphasis) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

