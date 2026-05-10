package com.schoolsync.teacher.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.schoolsync.teacher.data.model.ClassAssignment
import com.schoolsync.teacher.ui.theme.AppColors
import com.schoolsync.teacher.ui.theme.GradientBackground
import com.schoolsync.teacher.ui.theme.LocalAppColors
import com.schoolsync.teacher.ui.theme.glassCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val c = LocalAppColors.current

    // Profile-screen palette override — rose tones to differentiate the
    // teacher's "My Profile" page from the rest of the app's accent
    // system. Scoped to this composable; helper composables (Chip /
    // InfoCard / AssignmentsCard) remain accent-themed via their own
    // `c: AppColors` parameter, so other screens that render them still
    // pick up the global accent.
    val profileAccent          = Color(0xFFE11D48)   // rose-600
    val profileAccentSecondary = Color(0xFFF43F5E)   // rose-500

    GradientBackground {
        if (state.isLoading && state.staff == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = profileAccent)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                val doc = state.staff

                // ── Hero strip: avatar + name + chips ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(cornerRadius = 16.dp)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar
                    val initials = state.cachedName.split(" ").take(2)
                        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                        .joinToString("").ifEmpty { "T" }

                    if (state.cachedProfilePic.isNotEmpty()) {
                        AsyncImage(
                            model = state.cachedProfilePic,
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .border(2.dp, profileAccent, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(profileAccent, profileAccentSecondary))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(initials, color = c.textOnAccent,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    // Name + meta
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.cachedName,
                            style = MaterialTheme.typography.titleMedium,
                            color = c.textPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (state.cachedPosition.isNotEmpty() || state.cachedDepartment.isNotEmpty()) {
                            Text(
                                text = listOf(state.cachedPosition, state.cachedDepartment)
                                    .filter { it.isNotEmpty() }.joinToString(" \u00B7 "),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary, maxLines = 1
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (state.cachedSchoolName.isNotEmpty()) Chip(state.cachedSchoolName, profileAccent, profileAccent.copy(alpha = 0.14f))
                            if ((doc?.status ?: "").isNotEmpty()) Chip(doc!!.status, c.success, c.successSurface)
                            if ((doc?.employmentType ?: "").isNotEmpty()) Chip(doc!!.employmentType, c.info, c.infoSurface)
                        }
                    }

                    IconButton(onClick = viewModel::refresh, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Refresh, null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                // ── Class Teacher badge ──
                if (state.classTeacherSections.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(profileAccent.copy(alpha = 0.10f))
                            .border(1.dp, profileAccent.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Class, null, tint = profileAccent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Class Teacher  \u00B7  " + state.classTeacherSections.joinToString(", "),
                            style = MaterialTheme.typography.labelMedium,
                            color = profileAccent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    }
                }

                // ── My Assignments card ──
                if (state.assignments.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    AssignmentsCard(state.assignments, c)
                }

                Spacer(Modifier.height(14.dp))

                // ── Two-column grid of sections ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left column
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoCard("Personal", Icons.Filled.Person, profileAccent, listOfNotNull(
                            f("Name", state.cachedName),
                            f("Gender", doc?.gender),
                            f("DOB", doc?.dob),
                            f("Marital Status", doc?.maritalStatus),
                            f("Father's Name", doc?.fatherName),
                        ))

                        InfoCard("Employment", Icons.Filled.Work, c.info, listOfNotNull(
                            f("Designation", doc?.designation?.ifEmpty { doc.position }),
                            f("Department", doc?.department ?: state.cachedDepartment),
                            f("Type", doc?.employmentType),
                            f("Joined", doc?.dateOfJoining ?: doc?.joiningDate),
                            f("Subjects", doc?.teaching_subjects?.joinToString(", ")),
                        ))

                        val bank = doc?.bankDetails
                        if (bank != null && bank.isNotEmpty()) {
                            InfoCard("Bank", Icons.Filled.AccountBalance, c.slateBluePrimary, listOfNotNull(
                                f("Bank", bank["bankName"]?.toString()),
                                f("Holder", bank["accountHolderName"]?.toString()),
                                f("Account", maskAcct(bank["accountNumber"]?.toString())),
                                f("IFSC", bank["ifscCode"]?.toString()),
                            ))
                        }
                    }

                    // Right column
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoCard("Contact", Icons.Filled.Phone, c.success, listOfNotNull(
                            f("Email", doc?.email ?: state.cachedEmail),
                            f("Phone", doc?.phone ?: state.cachedPhone),
                            f("Alt. Phone", doc?.altPhone),
                            f("Emergency", buildEmergency(doc?.emergencyContact)),
                        ))

                        val hasStat = listOf(doc?.panNumber, doc?.aadharNumber, doc?.pfNumber, doc?.esiNumber)
                            .any { !it.isNullOrBlank() }
                        if (hasStat) {
                            InfoCard("Statutory IDs", Icons.Filled.CreditCard, c.warning, listOfNotNull(
                                f("PAN", doc?.panNumber),
                                f("Aadhar", doc?.aadharNumber),
                                f("PF / UAN", doc?.pfNumber),
                                f("ESI", doc?.esiNumber),
                            ))
                        }

                        val addr = doc?.addressMap
                        val permAddr = doc?.permanentAddress
                        if (addr != null) {
                            InfoCard("Address", Icons.Filled.Home, c.error, listOfNotNull(
                                f("Current", flatAddr(addr)),
                                if (permAddr != null && permAddr.isNotEmpty()) f("Permanent", flatAddr(permAddr)) else null,
                            ))
                        }

                        val qual = doc?.qualificationDetails
                        if (qual != null) {
                            InfoCard("Qualification", Icons.Filled.School, profileAccent, listOfNotNull(
                                f("Degree", qual["highestQualification"]?.toString()),
                                f("University", qual["university"]?.toString()),
                                f("Year", qual["yearOfPassing"]?.toString()),
                                f("Experience", qual["experience"]?.toString()?.let { "${it} yrs" }),
                            ))
                        }
                    }
                }

                // Error
                state.error?.let { err ->
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(cornerRadius = 10.dp)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Warning, null, tint = c.warning, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(err, style = MaterialTheme.typography.labelSmall, color = c.warning)
                    }
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

// ── Reusable components ──

@Composable
private fun Chip(text: String, textColor: Color, bgColor: Color) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun InfoCard(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    fields: List<Pair<String, String>>
) {
    val c = LocalAppColors.current
    val visible = fields.filter { it.second.isNotBlank() }
    if (visible.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 12.dp)
            .padding(12.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        visible.forEachIndexed { idx, (label, value) ->
            if (idx > 0) {
                Divider(
                    color = c.divider.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 5.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(0.38f)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(0.62f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Compact assignments table grouped by class-section. */
@Composable
private fun AssignmentsCard(assignments: List<ClassAssignment>, c: AppColors) {
    // Group by class+section
    val grouped = assignments.groupBy { "${it.className} / ${it.section}" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 12.dp)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(c.info.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MenuBook, null, tint = c.info, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "My Assignments",
                style = MaterialTheme.typography.labelLarge,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${assignments.size} subjects",
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary,
                fontSize = 9.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        grouped.forEach { (section, items) ->
            // Section header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(c.accentSurface)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = section,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.accent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f)
                )
                val isCT = items.any { it.classTeacher }
                if (isCT) {
                    Chip("Class Teacher", c.accent, c.accent.copy(alpha = 0.18f))
                }
            }

            // Subject rows
            items.forEach { a ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = a.subject,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textPrimary,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (a.classTeacher) {
                        Icon(Icons.Filled.Class, null, tint = c.accent, modifier = Modifier.size(12.dp))
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
        }
    }
}

// ── Helpers ──

private fun f(label: String, value: String?): Pair<String, String>? {
    val v = value?.trim() ?: ""
    return if (v.isNotEmpty()) label to v else null
}

private fun buildEmergency(ec: Map<String, Any>?): String? {
    if (ec == null) return null
    val name = ec["name"]?.toString()?.takeIf { it.isNotBlank() } ?: return null
    val rel = ec["relation"]?.toString()?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
    val phone = ec["phoneNumber"]?.toString()?.takeIf { it.isNotBlank() }?.let { " - $it" } ?: ""
    return "$name$rel$phone"
}

private fun flatAddr(addr: Map<String, Any>?): String? {
    if (addr == null) return null
    val parts = listOfNotNull(
        addr["Street"]?.toString() ?: addr["street"]?.toString(),
        addr["City"]?.toString() ?: addr["city"]?.toString(),
        addr["State"]?.toString() ?: addr["state"]?.toString(),
        (addr["PostalCode"]?.toString() ?: addr["postalCode"]?.toString())?.let { "- $it" }
    ).filter { it.isNotBlank() }
    return parts.joinToString(", ").takeIf { it.isNotBlank() }
}

private fun maskAcct(acct: String?): String? {
    if (acct.isNullOrBlank()) return null
    return if (acct.length > 4) "****${acct.takeLast(4)}" else acct
}
