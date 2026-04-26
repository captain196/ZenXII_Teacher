package com.schoolsync.teacher.ui.appraisals

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.teacher.data.model.firestore.AppraisalDoc
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

@Composable
@ReadOnlyComposable
private fun statusColor(status: String): Pair<Color, Color> = when (status) {
    "Reviewed" -> SuccessGreen to SuccessGreenSurface
    "Submitted" -> Teal to TealSurface
    else -> TextTertiary to Glass
}

@Composable
fun AppraisalsScreen(viewModel: AppraisalsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    GradientBackground {
        Scaffold(containerColor = Color.Transparent) { inner ->
            Column(Modifier.fillMaxSize().padding(inner)) {
                TopBar(onRefresh = viewModel::load, isLoading = state.isLoading)

                if (state.isLoading && state.appraisals.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Teal)
                    }
                } else if (state.error != null && state.appraisals.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(state.error ?: "", color = ErrorRed, fontSize = 14.sp)
                    }
                } else if (state.appraisals.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.appraisals, key = { it.id }) { a ->
                            AppraisalCard(
                                a = a,
                                expanded = state.expandedId == a.id,
                                onToggle = { viewModel.toggleExpand(a.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(onRefresh: () -> Unit, isLoading: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.WorkspacePremium, null, tint = Teal, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text("My Appraisals", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onRefresh, enabled = !isLoading) {
            Icon(Icons.Filled.Refresh, "Refresh", tint = if (isLoading) TextTertiary else Teal)
        }
    }
}

@Composable
private fun EmptyState() {
    com.schoolsync.teacher.ui.components.EmptyStatePro(
        icon = Icons.Filled.WorkspacePremium,
        title = "No appraisals yet",
        description = "Submitted performance reviews from HR will appear here.",
    )
}

@Composable
private fun AppraisalCard(a: AppraisalDoc, expanded: Boolean, onToggle: () -> Unit) {
    val (sColor, sBg) = statusColor(a.status)
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
                Text(a.period.ifEmpty { a.appraisalType }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text("${a.appraisalType} • Reviewer: ${a.reviewerName}", fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                StarRow(a.overallRating)
            }
            Column(horizontalAlignment = Alignment.End) {
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(sBg).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(a.status, color = sColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(4.dp))
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = TextTertiary, modifier = Modifier.size(20.dp))
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = 12.dp)) {
                Divider(color = DividerColor)
                Spacer(Modifier.height(10.dp))
                SectionHeader("Ratings (out of 10)")
                RatingBar("Teaching quality", a.teachingQuality)
                RatingBar("Punctuality", a.punctuality)
                RatingBar("Student feedback", a.studentFeedback)
                RatingBar("Initiative", a.initiative)
                RatingBar("Teamwork", a.teamwork)
                Spacer(Modifier.height(6.dp))
                RatingBar("Overall", a.overallRating, emphasis = true)
                if (a.strengths.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    SectionHeader("Strengths")
                    Text(a.strengths, color = TextPrimary, fontSize = 13.sp)
                }
                if (a.areasOfImprovement.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    SectionHeader("Areas of improvement")
                    Text(a.areasOfImprovement, color = TextPrimary, fontSize = 13.sp)
                }
                if (a.goals.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    SectionHeader("Goals")
                    Text(a.goals, color = TextPrimary, fontSize = 13.sp)
                }
                if (a.comments.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    SectionHeader("Reviewer comments")
                    Text(a.comments, color = TextPrimary, fontSize = 13.sp)
                }
                if (a.recommendation.isNotBlank() && a.recommendation != "none") {
                    Spacer(Modifier.height(10.dp))
                    SectionHeader("Recommendation")
                    Text(a.recommendation.replaceFirstChar { it.uppercase() }, color = Teal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
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
        letterSpacing = TextUnit(0.8f, TextUnitType.Sp),
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun RatingBar(label: String, value: Double, emphasis: Boolean = false) {
    val pct = (value / 10.0).toFloat().coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextSecondary, fontSize = 13.sp, fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Normal)
            Text(
                "${"%.1f".format(value)}/10",
                color = if (emphasis) Teal else TextPrimary,
                fontSize = 13.sp,
                fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = if (emphasis) Teal else TealSurface,
            trackColor = Glass,
            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap
        )
    }
}

@Composable
private fun StarRow(rating: Double) {
    val stars = (rating / 2.0).coerceIn(0.0, 5.0) // 0-10 scale → 5 stars
    Row {
        repeat(5) { i ->
            val icon = when {
                stars >= i + 1 -> Icons.Filled.Star
                stars >= i + 0.5 -> Icons.Filled.StarHalf
                else -> Icons.Filled.StarBorder
            }
            Icon(icon, null, tint = Teal, modifier = Modifier.size(16.dp))
        }
    }
}
