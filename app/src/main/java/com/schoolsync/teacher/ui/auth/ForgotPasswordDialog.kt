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
import androidx.compose.ui.res.stringResource
import com.schoolsync.teacher.util.localizedString

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
 * stringResource(R.string.login_forgot_password) dialog shown from the login screen. The teacher enters
 * their Teacher ID; an unauthenticated cloud function (getRecoveryContact)
 * resolves their school from the ID and returns the school's recovery
 * contact so they know who to call. The reset itself is performed by the
 * admin from the admin panel (which sets the `must_change_password` claim —
 * the app then forces a new password on next login).
 */
@Composable
fun ForgotPasswordDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var teacherId by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<LookupState>(LookupState.Idle) }

    // Hoisted: lookup() is a plain local function, not a composable scope.
    val msgEnterIdFirst = stringResource(R.string.fp_enter_id_first)
    val msgNotTeacher = stringResource(R.string.fp_not_teacher)

    fun lookup() {
        val id = teacherId.trim()
        if (id.isEmpty()) {
            state = LookupState.Failure(msgEnterIdFirst)
            return
        }
        // This is the Teacher app — only teacher (STA) accounts recover here.
        // Reject obvious non-teacher IDs (student / admin / super-admin) before
        // the lookup; the server also enforces this by role.
        if (Regex("^(STU|ADM|SSA|SUP|PAR)\\d+$").matches(id.uppercase())) {
            state = LookupState.Failure(msgNotTeacher)
            return
        }
        state = LookupState.Loading
        scope.launch {
            state = fetchRecoveryContact(context, id)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.login_forgot_password),
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
                    text = stringResource(R.string.fp_admin_only),
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
                    label = { Text(stringResource(R.string.login_teacher_id)) },
                    placeholder = { Text(stringResource(R.string.fp_id_hint)) },
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
                                stringResource(R.string.fp_looking_up),
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
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
            } else {
                Button(
                    onClick = { lookup() },
                    enabled = state !is LookupState.Loading
                ) { Text(stringResource(R.string.fp_look_up)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
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
                text = contact.schoolName.ifBlank { stringResource(R.string.fp_your_school) },
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }

        HorizontalDivider(thickness = 1.dp, color = Divider)

        if (contact.name.isNotBlank()) {
            ContactRow(Icons.Filled.Person, stringResource(R.string.common_admin), contact.name)
        }

        if (contact.phone.isNotBlank()) {
            ContactRow(Icons.Filled.Phone, stringResource(R.string.common_phone), contact.phone) {
                IconActionChip(Icons.Filled.Call, stringResource(R.string.common_call)) { dial(context, contact.phone) }
                WhatsAppChip { openWhatsApp(context, contact.phone) }
                CopyChip { copy(clipboard, context, contact.phone) }
            }
        }

        if (contact.email.isNotBlank()) {
            ContactRow(Icons.Filled.Email, stringResource(R.string.common_email), contact.email) {
                IconActionChip(Icons.Filled.Email, stringResource(R.string.common_email)) { sendEmail(context, contact.email, teacherSubject(loginId)) }
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
            contentDescription = stringResource(R.string.common_whatsapp),
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
        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.common_copy), tint = Teal, modifier = Modifier.size(15.dp))
    }
}

private fun teacherSubject(loginId: String) =
    "Password reset request – ZenXii Teacher"  /* i18n-ignore: email subject read by the school admin, not the staff member */ + if (loginId.isNotBlank()) " – $loginId" else ""

private fun dial(context: Context, number: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    }.onFailure { Toast.makeText(context, context.getString(R.string.fp_no_dialer), Toast.LENGTH_SHORT).show() }
}

private fun openWhatsApp(context: Context, number: String) {
    val digits = number.filter { it.isDigit() }
    if (digits.isEmpty()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")))
    }.onFailure { Toast.makeText(context, context.getString(R.string.fp_no_whatsapp), Toast.LENGTH_SHORT).show() }
}

private fun sendEmail(context: Context, email: String, subject: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        context.startActivity(intent)
    }.onFailure { Toast.makeText(context, context.getString(R.string.fp_no_email), Toast.LENGTH_SHORT).show() }
}

private fun copy(
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    context: Context,
    value: String,
) {
    clipboard.setText(AnnotatedString(value))
    Toast.makeText(context, context.getString(R.string.common_copied), Toast.LENGTH_SHORT).show()
}

private suspend fun fetchRecoveryContact(ctx: Context, userId: String): LookupState {
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
                ctx.localizedString(R.string.fp_no_contact_teacher_fmt, userId)
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
            ctx.getString(R.string.err_cannot_reach_server)
        )
    }
}
