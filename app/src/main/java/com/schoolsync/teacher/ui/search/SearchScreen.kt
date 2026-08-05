package com.schoolsync.teacher.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.teacher.ui.theme.LocalAppColors
import com.schoolsync.teacher.ui.theme.glassCard
import com.schoolsync.teacher.ui.theme.gradientBackground

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onNavigateRoute: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val c = LocalAppColors.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    // Auto-focus the field + raise the keyboard the moment the screen opens.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(modifier = Modifier.fillMaxSize().gradientBackground()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {

            // ── Search bar row ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = c.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .glassCard(16.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Search, null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (uiState.query.isEmpty()) {
                            Text(
                                "Search",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = uiState.query,
                            onValueChange = viewModel::onQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.textPrimary),
                            cursorBrush = SolidColor(c.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }
                    if (uiState.query.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable { viewModel.clearQuery() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Close, "Clear", tint = c.textTertiary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // ── Results ─────────────────────────────────────────────────
            val query = uiState.query.trim()
            val sections: List<Pair<SearchCategory, List<SearchResult>>> = remember(uiState) {
                val source = if (query.isEmpty()) uiState.browseFeatures else uiState.results
                source.groupBy { it.category }
                    .toList()
                    .sortedBy { it.first.ordinal }
            }

            if (query.isNotEmpty() && !uiState.isLoading && uiState.results.isEmpty()) {
                EmptyState(query = query)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp
                    )
                ) {
                    if (query.isEmpty()) {
                        item("hint") {
                            Text(
                                "Find a student, class, homework, notice or event — or jump to any section.",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textTertiary,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                    sections.forEach { (category, rows) ->
                        item("h_${category.name}") {
                            SectionHeader(category.label.uppercase())
                        }
                        items(rows.size, key = { rows[it].id }) { idx ->
                            val r = rows[idx]
                            ResultRow(result = r, onClick = { onNavigateRoute(r.route) })
                            Spacer(Modifier.height(8.dp))
                        }
                        item("s_${category.name}") { Spacer(Modifier.height(6.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val c = LocalAppColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = c.textTertiary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
    )
}

@Composable
private fun ResultRow(result: SearchResult, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(16.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(c.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Text(result.emoji, fontSize = 20.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.title,
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (result.subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    result.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyState(query: String) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🔍", fontSize = 40.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "No results for \"$query\"",
            style = MaterialTheme.typography.titleMedium,
            color = c.textSecondary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Try a student name, roll number, subject, or a section.",
            style = MaterialTheme.typography.bodySmall,
            color = c.textTertiary
        )
    }
}
