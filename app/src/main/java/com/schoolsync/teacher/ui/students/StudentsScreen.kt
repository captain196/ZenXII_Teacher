package com.schoolsync.teacher.ui.students

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.schoolsync.teacher.ui.theme.Divider as DividerColor
import com.schoolsync.teacher.ui.theme.ErrorRed
import com.schoolsync.teacher.ui.theme.ErrorRedSurface
import com.schoolsync.teacher.ui.theme.Glass
import com.schoolsync.teacher.ui.theme.GlassBorder
import com.schoolsync.teacher.ui.theme.GradientBackground
import com.schoolsync.teacher.ui.theme.SuccessGreen
import com.schoolsync.teacher.ui.theme.SuccessGreenSurface
import com.schoolsync.teacher.ui.theme.SurfaceDark
import com.schoolsync.teacher.ui.theme.InfoBlue
import com.schoolsync.teacher.ui.theme.InfoBlueSurface
import com.schoolsync.teacher.ui.theme.Teal
import com.schoolsync.teacher.ui.theme.TealSurface
import com.schoolsync.teacher.ui.theme.TextPrimary
import com.schoolsync.teacher.ui.theme.TextSecondary
import com.schoolsync.teacher.ui.theme.TextTertiary
import com.schoolsync.teacher.ui.theme.WarningAmber
import com.schoolsync.teacher.ui.theme.WarningAmberSurface
import com.schoolsync.teacher.ui.theme.WarningAmber
import com.schoolsync.teacher.ui.theme.WarningAmberSurface
import com.schoolsync.teacher.ui.theme.glassCard

@Composable
fun StudentsScreen(
    viewModel: StudentsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    GradientBackground {
        Row(modifier = Modifier.fillMaxSize()) {
            // Main student list
            Column(
                modifier = Modifier
                    .weight(if (state.selectedStudent != null) 0.55f else 1f)
                    .fillMaxHeight()
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp)
            ) {
                // Header row
                StudentsHeader(
                    state = state,
                    onClassSelected = viewModel::selectClass,
                    onSearchChanged = viewModel::onSearchQueryChange,
                    onRefresh = viewModel::refresh
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Student grid
                if (state.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Teal)
                    }
                } else if (state.filteredStudents.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.PersonSearch,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No students found",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 200.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        items(
                            state.filteredStudents,
                            key = { it.studentId }
                        ) { student ->
                            StudentCard(
                                student = student,
                                isSelected = state.selectedStudent?.studentId == student.studentId,
                                onClick = { viewModel.selectStudent(student) }
                            )
                        }
                    }
                }
            }

            // Detail panel (shows when a student is selected)
            AnimatedVisibility(
                visible = state.selectedStudent != null,
                enter = fadeIn() + slideInHorizontally { it / 2 },
                exit = fadeOut() + slideOutHorizontally { it / 2 }
            ) {
                state.selectedStudent?.let { student ->
                    StudentDetailPanel(
                        student = student,
                        onClose = { viewModel.selectStudent(null) },
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StudentsHeader(
    state: StudentsUiState,
    onClassSelected: (String, String) -> Unit,
    onSearchChanged: (String) -> Unit,
    onRefresh: () -> Unit
) {
    var classDropdownExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title
        Text(
            text = "Students",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Class dropdown
            Box {
                OutlinedButton(
                    onClick = { classDropdownExpanded = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = SolidColor(GlassBorder)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (state.selectedClassName.isNotEmpty())
                            "${state.selectedClassName}-${state.selectedSection}" else "Class",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                }

                DropdownMenu(
                    expanded = classDropdownExpanded,
                    onDismissRequest = { classDropdownExpanded = false },
                    modifier = Modifier.background(SurfaceDark)
                ) {
                    state.availableClasses.forEach { (cls, sec) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "$cls - $sec",
                                    color = if (cls == state.selectedClassName && sec == state.selectedSection) Teal else TextPrimary
                                )
                            },
                            onClick = {
                                onClassSelected(cls, sec)
                                classDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Search
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChanged,
                placeholder = { Text("Search...", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                },
                modifier = Modifier
                    .width(200.dp)
                    .height(40.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Teal,
                    unfocusedBorderColor = GlassBorder,
                    cursorColor = Teal,
                    focusedContainerColor = Glass.copy(alpha = 0.2f),
                    unfocusedContainerColor = Glass.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(10.dp)
            )

            // Count badge
            Text(
                text = "${state.filteredStudents.size} students",
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary
            )

            // Role badge
            Box(
                modifier = Modifier
                    .background(
                        color = if (state.isClassTeacher) TealSurface else InfoBlueSurface,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .border(
                        1.dp,
                        if (state.isClassTeacher) Teal.copy(alpha = 0.3f) else InfoBlue.copy(alpha = 0.3f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (state.isClassTeacher) "Class Teacher"
                    else "Subject: ${state.assignedSubjects.joinToString(", ").ifEmpty { "Assigned" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.isClassTeacher) Teal else InfoBlue,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = TextSecondary)
            }
        }
    }
}

@Composable
private fun StudentCard(
    student: StudentInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(
                cornerRadius = 12.dp,
                borderColor = if (isSelected) Teal.copy(alpha = 0.5f) else GlassBorder,
                backgroundColor = if (isSelected) TealSurface else Glass
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Profile pic or initials
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Teal.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (student.profilePicUrl.isNotEmpty()) {
                    AsyncImage(
                        model = student.profilePicUrl,
                        contentDescription = student.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = student.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Teal,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Roll: ${student.rollNo}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 10.sp
                )
                if (student.fatherName.isNotEmpty()) {
                    Text(
                        text = "F: ${student.fatherName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (student.phone.isNotEmpty()) {
                // Tap-to-dial: opens the system dialer pre-filled with the
                // parent's phone number. ACTION_DIAL needs no runtime permission.
                IconButton(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_DIAL,
                            Uri.parse("tel:${student.phone}")
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.Call,
                        contentDescription = "Call ${student.name}",
                        tint = Teal,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StudentDetailPanel(
    student: StudentInfo,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .glassCard()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header with back
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Close", tint = TextSecondary)
            }
            Text(
                text = "Student Profile",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(32.dp)) // Balance
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Profile photo
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Teal.copy(alpha = 0.15f))
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            if (student.profilePicUrl.isNotEmpty()) {
                AsyncImage(
                    model = student.profilePicUrl,
                    contentDescription = student.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = Teal,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = student.name,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "${student.className} - ${student.section} | Roll: ${student.rollNo}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))
        Divider(color = DividerColor, thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(12.dp))

        // Detail rows
        ProfileDetailRow("Student ID", student.studentId)
        ProfileDetailRow("Father's Name", student.fatherName)
        ProfileDetailRow("Mother's Name", student.motherName)
        ProfileDetailRow("Date of Birth", student.dob)
        ProfileDetailRow("Gender", student.gender)
        ProfileDetailRow("Phone", student.phone)
        ProfileDetailRow("Email", student.email)
        ProfileDetailRow("Address", student.address)
        ProfileDetailRow("Admission Date", student.admissionDate)

        // ── Fees snapshot: per-month paid chips ──
        // Data comes from the canonical Firestore collections that the
        // parent app writes on every successful payment, so this block
        // is always in sync with what the parent has paid.
        if (student.monthFee.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = DividerColor, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))
            FeeSnapshotCard(
                monthFee = student.monthFee
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FeeSnapshotCard(monthFee: Map<String, Int>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "FEES",
            style = MaterialTheme.typography.labelMedium,
            color = TextTertiary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (monthFee.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Monthly Status",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Sort chips by academic order (April → March) so the
            // teacher sees months in the same order parents do.
            val academicOrder = listOf(
                "April", "May", "June", "July", "August", "September",
                "October", "November", "December", "January", "February", "March",
                "Yearly Fees"
            )
            val sortedEntries = monthFee.entries.sortedBy { e ->
                val idx = academicOrder.indexOf(e.key); if (idx >= 0) idx else 99
            }
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                sortedEntries.forEach { (month, paid) ->
                    MonthChip(month = month, isPaid = paid >= 1)
                }
            }
        }
    }
}

@Composable
private fun MonthChip(month: String, isPaid: Boolean) {
    val bg  = if (isPaid) SuccessGreenSurface else WarningAmberSurface
    val fg  = if (isPaid) SuccessGreen        else WarningAmber
    val label = if (month == "Yearly Fees") "Yearly" else month.take(3)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isPaid) "✓" else "•",
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ProfileDetailRow(label: String, value: String) {
    if (value.isBlank()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.End
        )
    }
}
