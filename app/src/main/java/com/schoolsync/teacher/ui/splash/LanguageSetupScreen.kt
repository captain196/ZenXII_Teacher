package com.schoolsync.teacher.ui.splash

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schoolsync.teacher.R
import com.schoolsync.teacher.service.NotificationChannels
import com.schoolsync.teacher.ui.theme.LocalAppColors
import com.schoolsync.teacher.ui.theme.gradientBackground
import com.schoolsync.teacher.util.LocaleManager

/**
 * First-run language chooser. Shown once, before the walkthrough and login,
 * when [LocaleManager.hasExplicitChoice] is false.
 *
 * ## Why this is asked rather than detected
 *
 * Following the device locale silently would be wrong for this audience. Most
 * Indian handsets ship with English as the system language even when the owner
 * does not read English comfortably — which is exactly the parent this app is
 * for. Auto-detecting would hand them an English app with no way out before the
 * login screen. The device locale is used as a *hint* (pre-selected when we ship
 * it) but the choice is always offered.
 *
 * ## Why it is only shown once
 *
 * Language is a sticky, one-time decision — on comparable Indian apps the large
 * majority of users who pick a regional language never change it. Re-prompting
 * after logout would imply the app forgot. Returning users get the compact
 * selector on the login screen instead.
 *
 * Ordering matters: this precedes the walkthrough, whose copy is itself
 * translated. Choosing here means the walkthrough is already in the user's
 * language.
 */
@Composable
fun LanguageSetupScreen(onContinue: () -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current

    // Pre-select the device language when we ship it, otherwise English — a
    // starting point, not a decision.
    var selected by remember { mutableStateOf(LocaleManager.effectiveTag(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))

        Text(
            text = "ZenXii",
            style = TextStyle(
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp,
                color = c.textPrimary
            )
        )

        Spacer(Modifier.height(28.dp))

        Text(
            text = stringResource(R.string.lang_setup_title),
            style = TextStyle(
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                textAlign = TextAlign.Center
            )
        )
        // Always-Hindi second line: at this moment the app is still rendering in
        // the device locale, so a Hindi speaker on an English phone can still
        // recognise what is being asked.
        Text(
            text = stringResource(R.string.lang_setup_title_alt),
            style = TextStyle(fontSize = 15.sp, color = c.textSecondary, textAlign = TextAlign.Center),
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(24.dp))

        // Two-column grid, built from Rows rather than LazyVerticalGrid so the
        // whole screen scrolls as one on short displays.
        LocaleManager.SUPPORTED.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { (tag, endonym) ->
                    LanguageTile(
                        endonym = endonym,
                        isSelected = tag == selected,
                        onClick = { selected = tag },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Keep the last row aligned when the count is odd.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.lang_setup_subtitle),
            style = TextStyle(fontSize = 12.sp, color = c.textTertiary, textAlign = TextAlign.Center)
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                // Only restart if what is RENDERED actually changes. Most users
                // accept the pre-selected device language, and recreate() would
                // bounce them back through Splash for no visible difference.
                val needsRecreate = selected != LocaleManager.effectiveTag(context)
                // commit(), not apply(): recreate() reads this back synchronously
                // in attachBaseContext.
                LocaleManager.setLanguage(context, selected)
                // Channel names are cached by the OS at creation time, so they
                // must be rewritten in the new language.
                NotificationChannels.ensureChannels(context)
                if (needsRecreate) {
                    // Re-runs attachBaseContext with the new tag. The graph
                    // restarts at Splash, which now sees hasExplicitChoice() ==
                    // true and routes onward — this screen is not shown again.
                    (context as? Activity)?.recreate()
                } else {
                    onContinue()
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.accent)
        ) {
            Text(
                text = stringResource(R.string.lang_setup_continue),
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.bgStart)
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

/**
 * One language option. Labelled by endonym only — a parent who reads only
 * Gujarati cannot find the word "Gujarati", but can find "ગુજરાતી".
 */
@Composable
private fun LanguageTile(
    endonym: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    Box(
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (isSelected) Modifier
                    .background(c.accentSurface)
                    .border(2.dp, c.accent, RoundedCornerShape(14.dp))
                else Modifier
                    .background(Color.Transparent)
                    .border(1.dp, c.divider, RoundedCornerShape(14.dp))
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = endonym,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) c.accent else c.textPrimary
                )
            )
            if (isSelected) {
                Spacer(Modifier.size(6.dp))
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
