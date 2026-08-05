package com.schoolsync.teacher.ui.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.schoolsync.teacher.R
import com.schoolsync.teacher.ui.theme.Divider
import com.schoolsync.teacher.ui.theme.ErrorRed
import com.schoolsync.teacher.ui.theme.Teal
import com.schoolsync.teacher.ui.theme.TealSurface
import com.schoolsync.teacher.ui.theme.TextPrimary
import com.schoolsync.teacher.ui.theme.TextSecondary
import com.schoolsync.teacher.ui.theme.TextTertiary
import com.schoolsync.teacher.ui.theme.glassCard
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private data class RecoveryContact(
    val schoolName: String,
    val name: String,
    val phone: String,
    val email: String,
)

private sealed interface LookupState {
    data object Idle : LookupState
    data object Loading : LookupState
    data class Success(val contact: RecoveryContact) : LookupState
    data class Failure(val message: String) : LookupState
}

/**
 * "Forgot password?" dialog shown from the login screen. The teacher enters
 * their Teacher ID; an unauthenticated cloud function (getRecoveryContact)
 * resolves their school from the ID and returns the school's recovery
 * contact so they know who to call. The reset itself is performed by the
 * admin from the admin panel (which sets the `must_change_password` claim —
 * the app then forces a new password on next login).
 */
@Composable
fun ForgotPasswordDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var teacherId by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<LookupState>(LookupState.Idle) }

    fun lookup() {
        val id = teacherId.trim()
        if (id.isEmpty()) {
            state = LookupState.Failure("Enter your Teacher ID first.")
            return
        }
        // This is the Teacher app — only teacher (STA) accounts recover here.
        // Reject obvious non-teacher IDs (student / admin / super-admin) before
        // the lookup; the server also enforces this by role.
        if (Regex("^(STU|ADM|SSA|SUP|PAR)\\d+$").matches(id.uppercase())) {
            state = LookupState.Failure("That's not a teacher account. Enter your Teacher ID (e.g. STA0001).")
            return
        }
        state = LookupState.Loading
        scope.launch {
            state = fetchRecoveryContact(id)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Forgot password?",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Passwords can only be reset by your school's admin. " +
                        "Enter your Teacher ID to see who to contact.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.size(12.dp))

                OutlinedTextField(
                    value = teacherId,
                    onValueChange = {
                        teacherId = it
                        if (state is LookupState.Failure || state is LookupState.Success) {
                            state = LookupState.Idle
                        }
                    },
                    label = { Text("Teacher ID") },
                    placeholder = { Text("e.g. STA0001") },
                    singleLine = true,
                    enabled = state !is LookupState.Loading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { lookup() }),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                when (val s = state) {
                    is LookupState.Idle -> Unit
                    is LookupState.Loading -> {
                        Spacer(modifier = Modifier.size(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Teal
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "Looking up your school…",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                    is LookupState.Failure -> {
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            text = s.message,
                            style = MaterialTheme.typography.labelMedium,
                            color = ErrorRed
                        )
                    }
                    is LookupState.Success -> {
                        Spacer(modifier = Modifier.size(14.dp))
                        ContactCard(s.contact, teacherId.trim())
                    }
                }
            }
        },
        confirmButton = {
            if (state is LookupState.Success) {
                TextButton(onClick = onDismiss) { Text("Done") }
            } else {
                Button(
                    onClick = { lookup() },
                    enabled = state !is LookupState.Loading
                ) { Text("Look up") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ContactCard(contact: RecoveryContact, loginId: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(14.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // School header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.School,
                contentDescription = null,
                tint = Teal,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(ROW_GAP))
            Text(
                text = contact.schoolName.ifBlank { "Your school" },
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }

        HorizontalDivider(thickness = 1.dp, color = Divider)

        if (contact.name.isNotBlank()) {
            ContactRow(Icons.Filled.Person, "Admin", contact.name)
        }

        if (contact.phone.isNotBlank()) {
            ContactRow(Icons.Filled.Phone, "Phone", contact.phone) {
                IconActionChip(Icons.Filled.Call, "Call") { dial(context, contact.phone) }
                WhatsAppChip { openWhatsApp(context, contact.phone) }
                CopyChip { copy(clipboard, context, contact.phone) }
            }
        }

        if (contact.email.isNotBlank()) {
            ContactRow(Icons.Filled.Email, "Email", contact.email) {
                IconActionChip(Icons.Filled.Email, "Email") { sendEmail(context, contact.email, teacherSubject(loginId)) }
                CopyChip { copy(clipboard, context, contact.email) }
            }
        }
    }
}

// Leading-icon size + gap; reused so the value text and the action row below
// it line up on the same left edge (icon 18dp + gap 12dp = 30dp indent).
private val ROW_ICON = 18.dp
private val ROW_GAP = 12.dp
private val ROW_INDENT = 30.dp

@Composable
private fun ContactRow(
    icon: ImageVector,
    label: String,
    value: String,
    actions: (@Composable () -> Unit)? = null,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = label,
                tint = TextTertiary,
                modifier = Modifier.size(ROW_ICON)
            )
            Spacer(Modifier.size(ROW_GAP))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }
        }
        if (actions != null) {
            Row(
                modifier = Modifier.padding(start = ROW_INDENT, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) { actions() }
        }
    }
}

/** Circular accent-tinted icon action (Call, Email). */
@Composable
private fun IconActionChip(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(TealSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Teal, modifier = Modifier.size(17.dp))
    }
}

/** WhatsApp action — real brand logo on its green tint, no recoloring. */
@Composable
private fun WhatsAppChip(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(0x1A25D366))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_whatsapp),
            contentDescription = "WhatsApp",
            tint = Color.Unspecified,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun CopyChip(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(TealSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = Teal, modifier = Modifier.size(15.dp))
    }
}

private fun teacherSubject(loginId: String) =
    "Password reset request – ZenXii Teacher" + if (loginId.isNotBlank()) " – $loginId" else ""

private fun dial(context: Context, number: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    }.onFailure { Toast.makeText(context, "No dialer app found.", Toast.LENGTH_SHORT).show() }
}

private fun openWhatsApp(context: Context, number: String) {
    val digits = number.filter { it.isDigit() }
    if (digits.isEmpty()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")))
    }.onFailure { Toast.makeText(context, "Couldn't open WhatsApp.", Toast.LENGTH_SHORT).show() }
}

private fun sendEmail(context: Context, email: String, subject: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        context.startActivity(intent)
    }.onFailure { Toast.makeText(context, "No email app found.", Toast.LENGTH_SHORT).show() }
}

private fun copy(
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    context: Context,
    value: String,
) {
    clipboard.setText(AnnotatedString(value))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

private suspend fun fetchRecoveryContact(userId: String): LookupState {
    return try {
        val result = Firebase.functions
            .getHttpsCallable("getRecoveryContact")
            .call(mapOf("userId" to userId, "audience" to "teacher"))
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?> ?: emptyMap()
        val found = data["found"] as? Boolean ?: false
        if (!found) {
            LookupState.Failure(
                "No contact found for \"$userId\". Check your Teacher ID and try again."
            )
        } else {
            LookupState.Success(
                RecoveryContact(
                    schoolName = (data["schoolName"] as? String).orEmpty(),
                    name = (data["name"] as? String).orEmpty(),
                    phone = (data["number"] as? String).orEmpty(),
                    email = (data["email"] as? String).orEmpty(),
                )
            )
        }
    } catch (e: Exception) {
        LookupState.Failure(
            "Couldn't reach the server. Check your connection and try again."
        )
    }
}
