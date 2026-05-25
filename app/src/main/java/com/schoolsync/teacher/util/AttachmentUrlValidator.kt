package com.schoolsync.teacher.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Phase 1b (2026-05-15) — Teacher-side parity of the Parent app's
 * `AttachmentUrlValidator` (which closed Finding #30 on the Parent side
 * during Step 5). Surfaced by [HomeworkDetailPanel] when the teacher
 * wants to review their own previously-uploaded homework attachments.
 *
 * **Why a Teacher-side validator at all** — the URL on the Firestore
 * homework doc was written by the teacher's own client, but it travels
 * through a Firestore round-trip on the way back to the device for
 * rendering. A compromised admin account, schema-injection, or a
 * cross-tenant misread (theoretical) could substitute a hostile URL.
 * The defense costs almost nothing and matches the Parent posture.
 *
 * Allowlist policy (identical to Parent app's validator):
 *   • Scheme MUST be `https` (case-insensitive). No `http`, no `gs`,
 *     no `file`, no `content`, no `data`, no `javascript`, no `intent`.
 *   • Host MUST be exactly `firebasestorage.googleapis.com`.
 *
 * Open policy:
 *   • Dispatch via [Intent.ACTION_VIEW] with [Intent.FLAG_ACTIVITY_NEW_TASK]
 *     so the chosen handler launches into its own task stack.
 *   • [ActivityNotFoundException] surfaces "no app available" instead
 *     of crashing.
 *   • Any other exception surfaces a generic "could not open" message.
 *
 * Telemetry — emitted via [debugLog] to `cache/debug.log` (50KB ring):
 *   ACC_HW_ATTACHMENT_OPEN_REJECTED  reason=<…> fileName=<…> urlLen=<…>
 *   ACC_HW_ATTACHMENT_OPEN_START     fileName=<…>
 *   ACC_HW_ATTACHMENT_OPEN_OK        fileName=<…>
 *   ACC_HW_ATTACHMENT_OPEN_NO_HANDLER fileName=<…>
 *   ACC_HW_ATTACHMENT_OPEN_FAIL      fileName=<…> err=<…>
 *
 * The URL itself is NEVER logged — only its length. Forensic recipe:
 *   adb shell run-as com.schoolsync.teacher cat cache/debug.log \
 *     | grep ACC_HW_ATTACHMENT_OPEN_
 *
 * Out of scope for Phase 1b:
 *   • Inline PDF rendering
 *   • Image preview gallery
 *   • DownloadManager integration
 *   • Attachment editing / deletion
 *   • Student submission attachment open (= Phase 2)
 */
object AttachmentUrlValidator {

    /** Hosts allowed for [Intent.ACTION_VIEW] dispatch. */
    private val ALLOWED_HOSTS = setOf("firebasestorage.googleapis.com")

    /** Outcome of [validate]. Mutually exclusive. */
    sealed class Result {
        object Valid : Result()
        data class Invalid(val reason: Reason, val userMessage: String) : Result()
    }

    /** Why a URL was rejected. Recorded in telemetry; not user-facing. */
    enum class Reason {
        BLANK,
        MALFORMED,
        NOT_HTTPS,
        HOST_NOT_ALLOWED,
    }

    /**
     * Pure validation — no side effects, safe to call from any thread
     * (including a Composable's recomposition).
     */
    fun validate(rawUrl: String?): Result {
        val url = rawUrl?.trim().orEmpty()
        if (url.isEmpty()) {
            return Result.Invalid(Reason.BLANK, "Attachment link is empty.")
        }

        val uri: Uri = try {
            Uri.parse(url)
        } catch (_: Exception) {
            return Result.Invalid(Reason.MALFORMED, "Attachment link is not a valid URL.")
        }

        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme.isEmpty()) {
            return Result.Invalid(Reason.MALFORMED, "Attachment link is missing its scheme.")
        }
        if (scheme != "https") {
            return Result.Invalid(Reason.NOT_HTTPS, "Only secure (https) attachments can be opened.")
        }

        val host = uri.host?.lowercase().orEmpty()
        if (host.isEmpty()) {
            return Result.Invalid(Reason.MALFORMED, "Attachment link is missing its host.")
        }
        if (host !in ALLOWED_HOSTS) {
            return Result.Invalid(
                Reason.HOST_NOT_ALLOWED,
                "This attachment link is not from a trusted source."
            )
        }

        return Result.Valid
    }

    /**
     * Validate then launch [Intent.ACTION_VIEW]. Returns the [Result]
     * so the caller can distinguish dispatch vs. rejection in tests;
     * the user-facing toast is handled here so the call site stays a
     * one-liner.
     *
     * [fileName] is for telemetry only — never trusted, never echoed
     * into a Uri.
     */
    fun openAttachmentSafely(
        context: Context,
        rawUrl: String?,
        fileName: String
    ): Result {
        val result = validate(rawUrl)
        when (result) {
            is Result.Invalid -> {
                debugLog(
                    "ACC_HW_ATTACHMENT_OPEN_REJECTED reason=${result.reason} " +
                            "fileName=$fileName urlLen=${rawUrl?.length ?: 0}"
                )
                Toast.makeText(context, result.userMessage, Toast.LENGTH_SHORT).show()
            }
            Result.Valid -> {
                val safeUrl = rawUrl!!.trim()
                debugLog("ACC_HW_ATTACHMENT_OPEN_START fileName=$fileName")
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    debugLog("ACC_HW_ATTACHMENT_OPEN_OK fileName=$fileName")
                } catch (_: ActivityNotFoundException) {
                    debugLog("ACC_HW_ATTACHMENT_OPEN_NO_HANDLER fileName=$fileName")
                    Toast.makeText(
                        context,
                        "No app available to open this attachment.",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    debugLog(
                        "ACC_HW_ATTACHMENT_OPEN_FAIL fileName=$fileName " +
                                "err=${e.javaClass.simpleName}:${e.message}"
                    )
                    Toast.makeText(
                        context,
                        "Could not open this attachment.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        return result
    }
}
