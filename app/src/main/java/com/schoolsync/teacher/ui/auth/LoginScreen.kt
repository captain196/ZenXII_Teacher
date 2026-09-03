package com.schoolsync.teacher.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import android.app.Activity
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.teacher.R
import com.schoolsync.teacher.ui.theme.BgEnd
import com.schoolsync.teacher.ui.theme.BgStart
import com.schoolsync.teacher.ui.theme.ErrorRed
import com.schoolsync.teacher.ui.theme.Glass
import com.schoolsync.teacher.ui.theme.GlassBorder
import com.schoolsync.teacher.ui.theme.GradientBackground
import com.schoolsync.teacher.ui.theme.Teal
import com.schoolsync.teacher.ui.theme.TealDark
import com.schoolsync.teacher.ui.theme.TextPrimary
import com.schoolsync.teacher.ui.theme.TextSecondary
import com.schoolsync.teacher.ui.theme.TextTertiary
import com.schoolsync.teacher.ui.theme.glassCard
import com.schoolsync.teacher.service.NotificationChannels
import com.schoolsync.teacher.util.LocaleManager
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onRequiresPasswordChange: () -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val showForgotState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is LoginEvent.LoginSuccess -> onLoginSuccess()
                is LoginEvent.LoginRequiresPasswordChange -> onRequiresPasswordChange()
            }
        }
    }

    if (showForgotState.value) {
        ForgotPasswordDialog(onDismiss = { showForgotState.value = false })
    }

    GradientBackground {
        // Branding panel — `compact` trims the logo, type scale and drops the
        // feature bullets so it fits above the form on a narrow phone.
        val branding: @Composable (Boolean) -> Unit = { compact ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.zenxii_logo),
                    contentDescription = "ZenXii",
                    modifier = Modifier.size(if (compact) 72.dp else 96.dp)
                )

                Spacer(modifier = Modifier.height(if (compact) 16.dp else 24.dp))

                Text(
                    text = "ZenXii",
                    style = if (compact) MaterialTheme.typography.displaySmall
                    else MaterialTheme.typography.displayMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = stringResource(R.string.login_app_subtitle),
                    style = if (compact) MaterialTheme.typography.titleLarge
                    else MaterialTheme.typography.headlineMedium,
                    color = Teal,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(if (compact) 10.dp else 16.dp))

                Text(
                    text = stringResource(R.string.login_tagline),
                    style = if (compact) MaterialTheme.typography.bodyMedium
                    else MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                if (!compact) {
                    Spacer(modifier = Modifier.height(32.dp))

                    // Feature highlights
                    listOf(
                        stringResource(R.string.login_feature_attendance),
                        stringResource(R.string.login_feature_marks),
                        stringResource(R.string.login_feature_communicate)
                    ).forEach { feature ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Teal)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = feature,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Login form card — capped width so it never stretches edge-to-edge on
        // a tablet, full-width within its parent otherwise.
        val loginCard: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .glassCard()
                    .padding(28.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        Text(
                            text = stringResource(R.string.login_welcome_back),
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = stringResource(R.string.login_sign_in_to_continue),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
                        )

                        // Teacher ID field
                        OutlinedTextField(
                            value = state.userId,
                            onValueChange = viewModel::onUserIdChange,
                            label = { Text(stringResource(R.string.login_teacher_id)) },
                            placeholder = { Text(stringResource(R.string.login_enter_teacher_id)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = TextTertiary
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            colors = loginTextFieldColors(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Password field
                        OutlinedTextField(
                            value = state.password,
                            onValueChange = viewModel::onPasswordChange,
                            label = { Text(stringResource(R.string.login_password)) },
                            placeholder = { Text(stringResource(R.string.login_enter_password)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = TextTertiary
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = viewModel::togglePasswordVisibility) {
                                    Icon(
                                        imageVector = if (state.isPasswordVisible)
                                            Icons.Filled.VisibilityOff
                                        else
                                            Icons.Filled.Visibility,
                                        contentDescription = if (state.isPasswordVisible)
                                            stringResource(R.string.login_hide_password) else stringResource(R.string.login_show_password),
                                        tint = TextTertiary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (state.isPasswordVisible)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    viewModel.login()
                                }
                            ),
                            colors = loginTextFieldColors(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Error message
                        AnimatedVisibility(
                            visible = state.errorRes != null,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Text(
                                text = state.errorRes?.let { stringResource(it) } ?: "",
                                color = ErrorRed,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                textAlign = TextAlign.Start
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Login button
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.login()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            enabled = !state.isLoading,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Teal,
                                contentColor = BgStart,
                                disabledContainerColor = TealDark.copy(alpha = 0.5f)
                            )
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = BgStart,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.login_sign_in),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        androidx.compose.material3.TextButton(
                            onClick = { showForgotState.value = true }
                        ) {
                            Text(
                                text = stringResource(R.string.login_forgot_password),
                                style = MaterialTheme.typography.labelMedium,
                                color = Teal,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Compact language control, pinned top-right. Overlaid on the outer
            // box rather than placed inside either branch, so the wide
            // (landscape/tablet) and narrow layouts both get exactly one — and
            // so it never scrolls away with the form.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 12.dp, end = 16.dp)
                    .zIndex(1f)
            ) {
                LoginLanguageControl()
            }

            if (maxWidth >= 640.dp) {
                // Wide (tablet / landscape): branding and form side by side
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(end = 24.dp),
                        contentAlignment = Alignment.Center
                    ) { branding(false) }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .imePadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) { loginCard() }
                }
            } else {
                // Narrow (phone portrait): branding stacked above a full-width
                // form; the whole screen scrolls so nothing is ever clipped.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    branding(true)
                    Spacer(modifier = Modifier.height(28.dp))
                    loginCard()
                }
            }
        }
    }
}

@Composable
private fun loginTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = Teal,
    focusedBorderColor = Teal,
    unfocusedBorderColor = GlassBorder,
    focusedLabelColor = Teal,
    unfocusedLabelColor = TextTertiary,
    focusedLeadingIconColor = Teal,
    unfocusedLeadingIconColor = TextTertiary,
    focusedPlaceholderColor = TextTertiary,
    unfocusedPlaceholderColor = TextTertiary,
    focusedContainerColor = Glass.copy(alpha = 0.3f),
    unfocusedContainerColor = Glass.copy(alpha = 0.15f)
)

/**
 * Compact language control for the login screen.
 *
 * Deliberately NOT the full picker. The first-run chooser
 * ([com.schoolsync.teacher.ui.splash.LanguageSetupScreen]) already asked, and on
 * comparable Indian apps the overwhelming majority of users who pick a language
 * never change it — so showing six options above the login form every time is
 * clutter that competes with the Sign In action.
 *
 * This shows only the CURRENT language, in its own script, and opens a sheet on
 * tap — the same pattern Facebook and Instagram use on their login screens. It
 * still has to exist here, because a staff member who reads no English cannot
 * reach Profile to change it, and because a shared or handed-down device may
 * carry someone else's choice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginLanguageControl() {
    val context = LocalContext.current
    val current = LocaleManager.effectiveTag(context)
    var showSheet by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
            .clickable { showSheet = true }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "\uD83C\uDF10  " + LocaleManager.labelFor(current),  // i18n-ignore: emoji prefix
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(16.dp)
        )
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = BgStart
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.lang_sheet_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LocaleManager.SUPPORTED.forEach { (tag, endonym) ->
                    val selected = tag == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showSheet = false
                                if (tag != current) {
                                    LocaleManager.setLanguage(context, tag)
                                    NotificationChannels.ensureChannels(context)
                                    (context as? Activity)?.recreate()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = endonym,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) Teal else TextPrimary
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Teal,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
