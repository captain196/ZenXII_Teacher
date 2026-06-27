package com.schoolsync.teacher.ui.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schoolsync.teacher.ui.theme.Glass
import com.schoolsync.teacher.ui.theme.GradientBackground
import com.schoolsync.teacher.ui.theme.Teal
import com.schoolsync.teacher.ui.theme.TextPrimary
import com.schoolsync.teacher.ui.theme.TextSecondary

/**
 * MessagesScreen — Coming Soon variant.
 *
 * Direct messaging has been deferred while a modern in-app messaging
 * experience is designed. This screen renders a polished placeholder
 * so teachers see a clear product state rather than a dormant chat list.
 * Existing conversation history is preserved server-side in Firestore
 * (`conversations`, `messages`, `messageInboxes` collections) and will
 * become accessible again when messaging returns.
 *
 * Signature kept backwards-compatible with the previous chat screen so
 * the navigation graph composable call site `MessagesScreen()` continues
 * to compile without change.
 *
 * This Composable does NOT instantiate `MessagesViewModel`, so no RTDB
 * or chat-backend traffic is generated when the screen opens.
 */
@Composable
fun MessagesScreen() {
    GradientBackground {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Hero icon circle
                Surface(
                    shape = CircleShape,
                    color = Teal.copy(alpha = 0.14f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(112.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            tint = Teal,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // "Coming Soon" pill badge
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Teal.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "COMING SOON",
                        color = Teal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Title
                Text(
                    text = "Messaging Coming Soon",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Body copy
                Text(
                    text = "We're building a better way to communicate with parents and " +
                            "students. Until messaging returns, please use Circulars to " +
                            "broadcast updates and check Notices for school-wide announcements.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Preservation note
                Surface(
                    color = Glass,
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Existing conversations are safely preserved and will become " +
                                "available again when messaging returns.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
