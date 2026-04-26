package com.schoolsync.teacher.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.teacher.data.model.firestore.LibraryBookDoc
import com.schoolsync.teacher.data.model.firestore.LibraryFineDoc
import com.schoolsync.teacher.data.model.firestore.LibraryIssueDoc
import com.schoolsync.teacher.ui.theme.ErrorRed
import com.schoolsync.teacher.ui.theme.ErrorRedSurface
import com.schoolsync.teacher.ui.theme.Glass
import com.schoolsync.teacher.ui.theme.GlassBorder
import com.schoolsync.teacher.ui.theme.GradientBackground
import com.schoolsync.teacher.ui.theme.InfoBlue
import com.schoolsync.teacher.ui.theme.InfoBlueSurface
import com.schoolsync.teacher.ui.theme.SuccessGreen
import com.schoolsync.teacher.ui.theme.SuccessGreenSurface
import com.schoolsync.teacher.ui.theme.SurfaceDark
import com.schoolsync.teacher.ui.theme.Teal
import com.schoolsync.teacher.ui.theme.TextPrimary
import com.schoolsync.teacher.ui.theme.TextSecondary
import com.schoolsync.teacher.ui.theme.TextTertiary
import com.schoolsync.teacher.ui.theme.WarningAmber
import com.schoolsync.teacher.ui.theme.WarningAmberSurface
import com.schoolsync.teacher.ui.theme.glassCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun LibraryTeacherScreen(
    viewModel: LibraryTeacherViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    GradientBackground {
        Scaffold(
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Top bar
                LibraryTopBar(onRefresh = viewModel::refresh)

                // Tabs
                LibraryTabs(
                    selectedTab = state.selectedTab,
                    bookCount = state.books.size,
                    issuedCount = state.issuedBooks.size,
                    overdueCount = state.overdueBooks.size,
                    fineCount = state.fines.size,
                    onTabSelected = viewModel::selectTab
                )

                // Search bar (only on Catalog tab)
                if (state.selectedTab == 0) {
                    LibrarySearchBar(
                        query = state.searchQuery,
                        onQueryChange = viewModel::updateSearchQuery
                    )
                }

                // Content
                if (state.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Teal)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Loading library...",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    when (state.selectedTab) {
                        0 -> CatalogTab(books = state.books, modifier = Modifier.weight(1f))
                        1 -> IssuedTab(issues = state.issuedBooks, modifier = Modifier.weight(1f))
                        2 -> OverdueTab(issues = state.overdueBooks, modifier = Modifier.weight(1f))
                        3 -> FinesTab(fines = state.fines, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Error dialog
        state.error?.let { error ->
            AlertDialog(
                onDismissRequest = viewModel::clearError,
                title = { Text("Error", color = TextPrimary) },
                text = { Text(error, color = TextSecondary) },
                confirmButton = {
                    TextButton(onClick = viewModel::clearError) {
                        Text("OK", color = Teal)
                    }
                },
                containerColor = SurfaceDark,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

// ── Top Bar ──────────────────────────────────────────────────────────────────

@Composable
private fun LibraryTopBar(onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.MenuBook,
                contentDescription = null,
                tint = Teal,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = TextSecondary)
        }
    }
}

// ── Tabs ─────────────────────────────────────────────────────────────────────

@Composable
private fun LibraryTabs(
    selectedTab: Int,
    bookCount: Int,
    issuedCount: Int,
    overdueCount: Int,
    fineCount: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf(
        "Catalog" to bookCount,
        "Issued" to issuedCount,
        "Overdue" to overdueCount,
        "Fines" to fineCount
    )

    ScrollableTabRow(
        selectedTabIndex = selectedTab,
        containerColor = Color.Transparent,
        contentColor = Teal,
        edgePadding = 16.dp,
        indicator = {},
        divider = {}
    ) {
        tabs.forEachIndexed { index, (label, count) ->
            val isSelected = selectedTab == index
            val tabColor = when (index) {
                0 -> Teal
                1 -> InfoBlue
                2 -> ErrorRed
                3 -> WarningAmber
                else -> Teal
            }
            val tabBgColor = when (index) {
                0 -> if (isSelected) tabColor.copy(alpha = 0.12f) else Color.Transparent
                1 -> if (isSelected) InfoBlueSurface else Color.Transparent
                2 -> if (isSelected) ErrorRedSurface else Color.Transparent
                3 -> if (isSelected) WarningAmberSurface else Color.Transparent
                else -> Color.Transparent
            }

            Tab(
                selected = isSelected,
                onClick = { onTabSelected(index) },
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tabBgColor)
                    .then(
                        if (isSelected) Modifier.border(
                            1.dp,
                            tabColor.copy(alpha = 0.3f),
                            RoundedCornerShape(10.dp)
                        ) else Modifier
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) tabColor else TextSecondary
                    )
                    if (count > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) tabColor.copy(alpha = 0.15f) else Glass)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) tabColor else TextTertiary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Search Bar ───────────────────────────────────────────────────────────────

@Composable
private fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search books by title...") },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = Teal,
            focusedBorderColor = Teal,
            unfocusedBorderColor = GlassBorder,
            focusedLeadingIconColor = Teal,
            unfocusedLeadingIconColor = TextTertiary,
            focusedPlaceholderColor = TextTertiary,
            unfocusedPlaceholderColor = TextTertiary
        )
    )
}

// ── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    com.schoolsync.teacher.ui.components.EmptyStatePro(
        icon = icon,
        title = title,
        description = subtitle,
        modifier = modifier,
    )
}

// ── Catalog Tab ──────────────────────────────────────────────────────────────

@Composable
private fun CatalogTab(
    books: List<LibraryBookDoc>,
    modifier: Modifier = Modifier
) {
    if (books.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.LibraryBooks,
            title = "No books found",
            subtitle = "The library catalogue is empty",
            modifier = modifier
        )
    } else {
        LazyColumn(
            modifier = modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(books, key = { it.id }) { book ->
                BookCard(book = book)
            }
        }
    }
}

@Composable
private fun BookCard(book: LibraryBookDoc) {
    val isAvailable = book.availableCopies > 0
    val statusColor = if (isAvailable) SuccessGreen else ErrorRed
    val statusText = if (isAvailable) "Available" else "Unavailable"
    val categoryColor = getCategoryColor(book.category)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 14.dp)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Book icon placeholder
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(categoryColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Book,
                contentDescription = null,
                tint = categoryColor,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = book.authors.joinToString(", ").ifBlank { "Unknown Author" },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category chip
                if (book.category.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(categoryColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = book.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = categoryColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        )
                    }
                }

                // Shelf location
                if (book.shelf.isNotBlank()) {
                    Text(
                        text = "Shelf: ${book.shelf}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Status & copy count
        Column(horizontalAlignment = Alignment.End) {
            // Status badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(statusColor.copy(alpha = 0.12f))
                    .border(0.5.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${book.availableCopies}/${book.totalCopies}",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "copies",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                fontSize = 9.sp
            )
        }
    }
}

// ── Issued Tab ───────────────────────────────────────────────────────────────

@Composable
private fun IssuedTab(
    issues: List<LibraryIssueDoc>,
    modifier: Modifier = Modifier
) {
    if (issues.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.AutoStories,
            title = "No issued books",
            subtitle = "No books are currently issued",
            modifier = modifier
        )
    } else {
        LazyColumn(
            modifier = modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(issues, key = { it.id }) { issue ->
                IssueCard(issue = issue, isOverdue = false)
            }
        }
    }
}

// ── Overdue Tab ──────────────────────────────────────────────────────────────

@Composable
private fun OverdueTab(
    issues: List<LibraryIssueDoc>,
    modifier: Modifier = Modifier
) {
    if (issues.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Warning,
            title = "No overdue books",
            subtitle = "All books have been returned on time",
            modifier = modifier
        )
    } else {
        LazyColumn(
            modifier = modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(issues, key = { it.id }) { issue ->
                IssueCard(issue = issue, isOverdue = true)
            }
        }
    }
}

@Composable
private fun IssueCard(issue: LibraryIssueDoc, isOverdue: Boolean) {
    val accentColor = if (isOverdue) ErrorRed else InfoBlue
    val bgColor = if (isOverdue) ErrorRedSurface else InfoBlueSurface
    val daysOverdue = calculateDaysOverdue(issue.dueDate)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Glass)
            .border(
                1.dp,
                if (isOverdue) ErrorRed.copy(alpha = 0.2f) else GlassBorder,
                RoundedCornerShape(14.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Left color indicator
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Borrower icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = issue.bookTitle.ifBlank { "Unknown Book" },
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = issue.borrowerName.ifBlank { issue.borrowerId },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (issue.issueDate.isNotBlank()) {
                    Text(
                        text = "Issued: ${issue.issueDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                }
                if (issue.dueDate.isNotBlank()) {
                    Text(
                        text = "Due: ${issue.dueDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOverdue) ErrorRed else TextTertiary,
                        fontWeight = if (isOverdue) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Status badge
        Column(horizontalAlignment = Alignment.End) {
            if (isOverdue && daysOverdue > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ErrorRedSurface)
                        .border(0.5.dp, ErrorRed.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$daysOverdue",
                            style = MaterialTheme.typography.titleMedium,
                            color = ErrorRed,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (daysOverdue == 1L) "day" else "days",
                            style = MaterialTheme.typography.labelSmall,
                            color = ErrorRed.copy(alpha = 0.8f),
                            fontSize = 9.sp
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(bgColor)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Issued",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    )
                }
                if (issue.dueDate.isNotBlank()) {
                    val daysLeft = calculateDaysLeft(issue.dueDate)
                    if (daysLeft >= 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (daysLeft == 0L) "Due today" else "$daysLeft days left",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (daysLeft <= 2) WarningAmber else TextTertiary,
                            fontWeight = if (daysLeft <= 2) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

// ── Fines Tab ────────────────────────────────────────────────────────────────

@Composable
private fun FinesTab(
    fines: List<LibraryFineDoc>,
    modifier: Modifier = Modifier
) {
    if (fines.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.MonetizationOn,
            title = "No pending fines",
            subtitle = "All fines have been cleared",
            modifier = modifier
        )
    } else {
        // Total pending amount
        val totalPending = fines.sumOf { it.fineAmount }

        LazyColumn(
            modifier = modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Summary card
            item(key = "summary") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(WarningAmberSurface)
                        .border(1.dp, WarningAmber.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Total Pending",
                            style = MaterialTheme.typography.labelMedium,
                            color = WarningAmber.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "${fines.size} fines",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                    Text(
                        text = String.format("%.2f", totalPending),
                        style = MaterialTheme.typography.headlineSmall,
                        color = WarningAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            items(fines, key = { it.id }) { fine ->
                FineCard(fine = fine)
            }
        }
    }
}

@Composable
private fun FineCard(fine: LibraryFineDoc) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(cornerRadius = 14.dp)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Warning icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(WarningAmber.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.MonetizationOn,
                contentDescription = null,
                tint = WarningAmber,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fine.borrowerName.ifBlank { fine.borrowerId },
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = fine.bookTitle.ifBlank { "Book: ${fine.bookId}" },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            // Reason chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(ErrorRedSurface)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = fine.reason.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = ErrorRed,
                    fontSize = 9.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Amount
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = String.format("%.2f", fine.fineAmount),
                style = MaterialTheme.typography.titleMedium,
                color = WarningAmber,
                fontWeight = FontWeight.Bold
            )
            // Status badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(WarningAmberSurface)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = fine.status.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = WarningAmber,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp
                )
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private val dateFormats = listOf(
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
    SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()),
    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
)

private fun parseDate(dateStr: String): Date? {
    for (format in dateFormats) {
        try {
            return format.parse(dateStr)
        } catch (_: Exception) { }
    }
    return null
}

private fun calculateDaysOverdue(dueDate: String): Long {
    if (dueDate.isBlank()) return 0
    val due = parseDate(dueDate) ?: return 0
    val now = Date()
    val diff = now.time - due.time
    return if (diff > 0) TimeUnit.MILLISECONDS.toDays(diff) else 0
}

private fun calculateDaysLeft(dueDate: String): Long {
    if (dueDate.isBlank()) return -1
    val due = parseDate(dueDate) ?: return -1
    val now = Date()
    val diff = due.time - now.time
    return if (diff >= 0) TimeUnit.MILLISECONDS.toDays(diff) else -1
}

@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
private fun getCategoryColor(category: String): Color {
    val lower = category.lowercase()
    return when {
        "fiction" in lower || "novel" in lower -> InfoBlue
        "science" in lower || "physics" in lower || "chemistry" in lower -> SuccessGreen
        "math" in lower -> Color(0xFF3B82F6)
        "history" in lower || "social" in lower -> Color(0xFF8B5CF6)
        "reference" in lower || "encyclopedia" in lower -> WarningAmber
        "biography" in lower -> Color(0xFFEC4899)
        "computer" in lower || "technology" in lower -> Color(0xFF06B6D4)
        else -> Teal
    }
}
