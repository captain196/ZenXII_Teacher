package com.schoolsync.teacher.ui.recruitment

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
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.teacher.data.model.firestore.RecruitmentDoc
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
import androidx.compose.ui.res.stringResource
import com.schoolsync.teacher.R
import androidx.compose.ui.res.pluralStringResource

@Composable
fun RecruitmentScreen(viewModel: RecruitmentViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    GradientBackground {
        Scaffold(containerColor = Color.Transparent) { inner ->
            Column(Modifier.fillMaxSize().padding(inner)) {
                TopBar(onRefresh = viewModel::load, isLoading = state.isLoading, count = state.openings.size)

                if (state.isLoading && state.openings.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Teal)
                    }
                } else if (state.error != null && state.openings.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(state.error ?: "", color = ErrorRed, fontSize = 14.sp)
                    }
                } else if (state.openings.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.openings, key = { it.id }) { job ->
                            JobCard(
                                job = job,
                                expanded = state.expandedId == job.id,
                                onToggle = { viewModel.toggleExpand(job.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(onRefresh: () -> Unit, isLoading: Boolean, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.WorkOutline, null, tint = Teal, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.rec_open_positions), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        if (count > 0) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(TealSurface).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text("$count", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onRefresh, enabled = !isLoading) {
            Icon(Icons.Filled.Refresh, "Refresh", tint = if (isLoading) TextTertiary else Teal)
        }
    }
}

@Composable
private fun EmptyState() {
    com.schoolsync.teacher.ui.components.EmptyStatePro(
        icon = Icons.Filled.WorkOutline,
        title = stringResource(R.string.rec_none),
        description = stringResource(R.string.rec_none_hint),
    )
}

@Composable
private fun JobCard(job: RecruitmentDoc, expanded: Boolean, onToggle: () -> Unit) {
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
                Text(job.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Apartment, null, tint = TextTertiary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(job.department.ifEmpty { "—" }, fontSize = 12.sp, color = TextSecondary)
                    if (job.vacancies > 0) {
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Filled.Groups, null, tint = TextTertiary, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(pluralStringResource(R.plurals.rec_vacancies_count, job.vacancies, job.vacancies), fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(SuccessGreenSurface).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(stringResource(R.string.common_open), color = SuccessGreen, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(4.dp))
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = TextTertiary, modifier = Modifier.size(20.dp))
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = 12.dp)) {
                Divider(color = DividerColor)
                Spacer(Modifier.height(10.dp))
                if (job.qualification.isNotBlank()) MetaRow(Icons.Filled.School, stringResource(R.string.profile_card_qualification), job.qualification)
                if (job.experience.isNotBlank()) MetaRow(Icons.Filled.WorkOutline, stringResource(R.string.rec_experience), job.experience)
                if (job.salaryRange.isNotBlank()) MetaRow(Icons.Filled.CurrencyRupee, stringResource(R.string.rec_salary), job.salaryRange)
                if (job.closingDate.isNotBlank()) MetaRow(Icons.Filled.CalendarToday, stringResource(R.string.rec_apply_by), job.closingDate)
                if (job.jobDescription.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    SectionHeader(stringResource(R.string.common_description))
                    Text(job.jobDescription, color = TextPrimary, fontSize = 13.sp)
                }
                if (job.postedDate.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.rec_posted_fmt, job.postedDate), color = TextTertiary, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun MetaRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = Teal, modifier = Modifier.size(16.dp).padding(top = 1.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = TextSecondary, fontSize = 11.sp)
            Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
