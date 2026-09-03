package com.schoolsync.teacher.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.teacher.ui.theme.ErrorRed
import com.schoolsync.teacher.ui.theme.Glass
import com.schoolsync.teacher.ui.theme.GlassBorder
import com.schoolsync.teacher.ui.theme.GradientBackground
import com.schoolsync.teacher.ui.theme.Teal
import com.schoolsync.teacher.ui.theme.TealDark
import com.schoolsync.teacher.ui.theme.TealSurface
import com.schoolsync.teacher.ui.theme.TextOnAccent
import com.schoolsync.teacher.ui.theme.TextPrimary
import com.schoolsync.teacher.ui.theme.TextSecondary
import com.schoolsync.teacher.ui.theme.TextTertiary
import com.schoolsync.teacher.ui.theme.glassCard
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.res.stringResource
import com.schoolsync.teacher.R

/**
 * Force-change-password screen. Shown when a teacher logs in with a password
 * the school admin reset for them — their Firebase Auth account carries a
 * `must_change_password=true` custom claim and the navigation gate routes
 * here, blocking access to the rest of the app until they pick a permanent
 * password. The submit path calls /auth/clear_must_change which updates
 * Firebase Auth and clears the claim server-side.
 */
@Composable
fun ForceChangePasswordScreen(
    onDone: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ForceChangePasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ForceChangePasswordEvent.Success -> onDone()
                is ForceChangePasswordEvent.LoggedOut -> onLogout()
            }
        }
    }

    GradientBackground {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            // Adaptive sizing: shrink the icon, card padding, and vertical
            // rhythm on small phones so the form still breathes when the
            // IME is open. The thresholds are deliberately generous —
            // anything below ~360dp wide is one-handed phone territory.
            val isCompactWidth = maxWidth < 360.dp
            val isShortHeight = maxHeight < 640.dp

            val iconSize = if (isCompactWidth) 56.dp else 72.dp
            val iconBgSize = iconSize + 16.dp
            val cardPadding = if (isCompactWidth) 22.dp else 28.dp
            val titleSize = if (isCompactWidth) 22.sp else 24.sp
            val sectionGap = if (isShortHeight) 14.dp else 20.dp

            val viewportHeight = maxHeight

            // Sign-out escape hatch — top-right. Without it the teacher is
            // trapped on this screen if they accidentally signed into the
            // wrong account and have no way back to the login screen.
            IconButton(
                onClick = viewModel::logout,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Logout,
                    contentDescription = stringResource(R.string.common_sign_out),
                    tint = TextSecondary,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = viewportHeight)
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .fillMaxWidth()
                            .glassCard()
                            .padding(cardPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(iconBgSize)
                                .clip(CircleShape)
                                .background(TealSurface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LockReset,
                                contentDescription = null,
                                tint = Teal,
                                modifier = Modifier.size(iconSize - 24.dp),
                            )
                        }

                        Spacer(Modifier.height(sectionGap))

                        Text(
                            text = stringResource(R.string.fcp_title),
                            color = TextPrimary,
                            fontSize = titleSize,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.fcp_reset_by_admin),
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(sectionGap + 4.dp))

                        OutlinedTextField(
                            value = state.newPassword,
                            onValueChange = viewModel::onNewPasswordChange,
                            label = { Text(stringResource(R.string.fcp_new_password)) },
                            singleLine = true,
                            visualTransformation = if (state.newVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                            ),
                            trailingIcon = {
                                IconButton(onClick = viewModel::toggleNewVisibility) {
                                    Icon(
                                        imageVector = if (state.newVisible) {
                                            Icons.Filled.VisibilityOff
                                        } else {
                                            Icons.Filled.Visibility
                                        },
                                        contentDescription = if (state.newVisible) stringResource(R.string.common_hide) else stringResource(R.string.common_show),
                                        tint = TextTertiary,
                                    )
                                }
                            },
                            isError = state.errorMessage != null,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Teal,
                                unfocusedBorderColor = GlassBorder,
                                errorBorderColor = ErrorRed,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = state.confirmPassword,
                            onValueChange = viewModel::onConfirmPasswordChange,
                            label = { Text(stringResource(R.string.fcp_confirm_new_password)) },
                            singleLine = true,
                            visualTransformation = if (state.confirmVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    viewModel.submit()
                                },
                            ),
                            trailingIcon = {
                                IconButton(onClick = viewModel::toggleConfirmVisibility) {
                                    Icon(
                                        imageVector = if (state.confirmVisible) {
                                            Icons.Filled.VisibilityOff
                                        } else {
                                            Icons.Filled.Visibility
                                        },
                                        contentDescription = if (state.confirmVisible) stringResource(R.string.common_hide) else stringResource(R.string.common_show),
                                        tint = TextTertiary,
                                    )
                                }
                            },
                            isError = state.errorMessage != null,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Teal,
                                unfocusedBorderColor = GlassBorder,
                                errorBorderColor = ErrorRed,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = stringResource(R.string.fcp_password_rule),
                            color = TextTertiary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (state.errorMessage != null) {
                            Spacer(Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ErrorRed.copy(alpha = 0.10f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = state.errorMessage!!,
                                    color = ErrorRed,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    textAlign = TextAlign.Start,
                                )
                            }
                        }

                        Spacer(Modifier.height(sectionGap + 4.dp))

                        Button(
                            onClick = viewModel::submit,
                            enabled = !state.isSubmitting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Teal,
                                contentColor = TextOnAccent,
                                disabledContainerColor = TealDark.copy(alpha = 0.6f),
                                disabledContentColor = TextOnAccent.copy(alpha = 0.7f),
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                        ) {
                            if (state.isSubmitting) {
                                CircularProgressIndicator(
                                    color = TextOnAccent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(22.dp),
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.fcp_save_continue),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        TextButton(
                            onClick = viewModel::logout,
                            enabled = !state.isSubmitting,
                        ) {
                            Text(
                                text = stringResource(R.string.fcp_sign_out_instead),
                                fontSize = 13.sp,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
