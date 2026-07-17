package com.noop.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.noop.update.UpdateCheck
import kotlin.random.Random

/**
 * Friends network — private pipe for people you trust (LAN / Tailscale only).
 *
 * Product story: invite codes, opt-in Charge/Effort day shares, install help for friends
 * already on your network. **Not** the app-update path (that stays GitHub).
 * Design: `docs/agent/FRIENDS_NETWORK.md`.
 */
@Composable
fun FriendsNetworkScreen() {
    val context = LocalContext.current
    var inviteCode by remember { mutableStateOf(FriendsNetworkPrefs.inviteCode(context)) }
    var enterCode by remember { mutableStateOf("") }
    var shareCharge by remember { mutableStateOf(FriendsNetworkPrefs.shareChargeDay(context)) }
    var shareEffort by remember { mutableStateOf(FriendsNetworkPrefs.shareEffortDay(context)) }
    var shareRestNote by remember { mutableStateOf(FriendsNetworkPrefs.shareRestNote(context)) }

    ScreenScaffold(
        title = "Friends network",
        subtitle = "Private pipe · no cloud accounts",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space16)) {
            Text(
                "Invite people you trust onto the same LAN or Tailscale tailnet. " +
                    "Share opt-in Charge and Effort day cards — not raw strap streams. " +
                    "App updates still come from GitHub; Friends is only the private friend pipe.",
                style = NoopType.subhead,
                color = Palette.textSecondary,
            )

            // How it works
            FriendsStoryCard(
                title = "How it works",
                lines = listOf(
                    "1. Friend joins your private network (home Wi‑Fi or Tailscale).",
                    "2. You share an invite code or the invite text.",
                    "3. They enter the code in NOOP → Friends network.",
                    "4. Opt-in day shares stay on the private pipe — never a public cloud.",
                ),
            )

            // Network pipe
            FriendsStoryCard(
                title = "Private network pipe",
                lines = listOf(
                    "LAN first when you’re on the same Wi‑Fi.",
                    "Tailscale when away — the only place Tailscale appears in NOOP.",
                    "No public discovery. No WHOOP cloud. No AI store update path here.",
                ),
            )

            // Invite
            NoopCard {
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.space12)) {
                    Text("Invite", style = NoopType.headline, color = Palette.textPrimary)
                    Text(
                        "Friends must already be on your tailnet or LAN. Share installs the app; " +
                            "the invite code links your phones for opt-in day shares.",
                        style = NoopType.footnote,
                        color = Palette.textTertiary,
                    )
                    NoopButton(
                        text = "Share invite",
                        leadingIcon = Icons.Filled.IosShare,
                        kind = NoopButtonKind.Primary,
                        fullWidth = true,
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "NOOP Friends network")
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    UpdateCheck.friendsNetworkShareText(inviteCode),
                                )
                            }
                            try {
                                context.startActivity(
                                    Intent.createChooser(send, "Invite friends"),
                                )
                            } catch (_: ActivityNotFoundException) {
                                copyText(
                                    context,
                                    "NOOP Friends invite",
                                    UpdateCheck.friendsNetworkShareText(inviteCode),
                                )
                                Toast.makeText(context, "Invite text copied.", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    NoopButton(
                        text = "Copy invite text",
                        leadingIcon = Icons.Filled.ContentCopy,
                        kind = NoopButtonKind.Secondary,
                        fullWidth = true,
                        onClick = {
                            copyText(
                                context,
                                "NOOP Friends invite",
                                UpdateCheck.friendsNetworkShareText(inviteCode),
                            )
                            Toast.makeText(context, "Invite text copied.", Toast.LENGTH_SHORT).show()
                        },
                    )
                    NoopButton(
                        text = "Copy AltStore source (iPhone)",
                        leadingIcon = Icons.Filled.Link,
                        kind = NoopButtonKind.Tertiary,
                        fullWidth = true,
                        onClick = {
                            copyText(context, "AltStore source", UpdateCheck.ALTSTORE_SOURCE_URL)
                            Toast.makeText(
                                context,
                                "Copied. In AltStore: Sources → + → paste.",
                                Toast.LENGTH_LONG,
                            ).show()
                        },
                    )
                }
            }

            // Invite codes
            NoopCard {
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.space12)) {
                    Text("Invite codes", style = NoopType.headline, color = Palette.textPrimary)
                    Text(
                        "Short codes for the private handshake. Peer discovery ships later — " +
                            "generate and share now so the product story is clear.",
                        style = NoopType.footnote,
                        color = Palette.textTertiary,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Palette.surfaceInset)
                            .border(1.dp, Palette.hairline, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IconKey()
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Your code", style = NoopType.caption, color = Palette.textTertiary)
                            Text(
                                inviteCode.ifBlank { "—" },
                                style = NoopType.title2,
                                color = Palette.textPrimary,
                                modifier = Modifier.semantics {
                                    contentDescription = "Your Friends invite code"
                                },
                            )
                        }
                    }
                    NoopButton(
                        text = if (inviteCode.isBlank()) "Generate invite code" else "Regenerate code",
                        leadingIcon = Icons.Filled.Key,
                        kind = NoopButtonKind.Secondary,
                        fullWidth = true,
                        onClick = {
                            val next = FriendsNetworkPrefs.newInviteCode()
                            FriendsNetworkPrefs.setInviteCode(context, next)
                            inviteCode = next
                            Toast.makeText(context, "Invite code ready.", Toast.LENGTH_SHORT).show()
                        },
                    )
                    if (inviteCode.isNotBlank()) {
                        NoopButton(
                            text = "Copy code",
                            leadingIcon = Icons.Filled.ContentCopy,
                            kind = NoopButtonKind.Tertiary,
                            fullWidth = true,
                            onClick = {
                                copyText(context, "NOOP invite code", inviteCode)
                                Toast.makeText(context, "Code copied.", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                    OutlinedTextField(
                        value = enterCode,
                        onValueChange = { enterCode = it.trim().uppercase().take(12) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Enter invite code" },
                        label = { Text("Enter friend’s code") },
                        placeholder = { Text("NOOP-AB12") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Palette.accent,
                            unfocusedBorderColor = Palette.hairline,
                            focusedLabelColor = Palette.accent,
                            cursorColor = Palette.accent,
                        ),
                    )
                    NoopButton(
                        text = "Save friend’s code",
                        leadingIcon = Icons.Filled.Group,
                        kind = NoopButtonKind.Secondary,
                        fullWidth = true,
                        enabled = enterCode.length >= 4,
                        onClick = {
                            FriendsNetworkPrefs.setFriendInviteCode(context, enterCode)
                            Toast.makeText(
                                context,
                                "Saved. Peer link will use this when discovery ships.",
                                Toast.LENGTH_LONG,
                            ).show()
                            enterCode = ""
                        },
                    )
                }
            }

            // What you can share
            NoopCard {
                Column(verticalArrangement = Arrangement.spacedBy(Metrics.space12)) {
                    Text("What you can share", style = NoopType.headline, color = Palette.textPrimary)
                    Text(
                        "Default off. High-level day cards only — never raw PPG or invented vitals. " +
                            "Sync over the private pipe lands after invite handshake.",
                        style = NoopType.footnote,
                        color = Palette.textTertiary,
                    )
                    FriendsShareToggle(
                        title = "Charge (day)",
                        blurb = "Today’s Charge score and band label.",
                        checked = shareCharge,
                        onCheckedChange = {
                            shareCharge = it
                            FriendsNetworkPrefs.setShareChargeDay(context, it)
                        },
                    )
                    FriendsShareToggle(
                        title = "Effort (day)",
                        blurb = "Today’s Effort / day-load summary.",
                        checked = shareEffort,
                        onCheckedChange = {
                            shareEffort = it
                            FriendsNetworkPrefs.setShareEffortDay(context, it)
                        },
                    )
                    FriendsShareToggle(
                        title = "Rest note",
                        blurb = "Optional one-line Rest cue — no stage chart dump.",
                        checked = shareRestNote,
                        onCheckedChange = {
                            shareRestNote = it
                            FriendsNetworkPrefs.setShareRestNote(context, it)
                        },
                    )
                }
            }

            // Honesty footer
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = Palette.textTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    "Bug reports use GitHub Issues (user-bug). Check for updates uses GitHub Releases. " +
                        "Tailscale stays here only — as the private friend pipe when you’re away from home Wi‑Fi.",
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Wifi,
                    contentDescription = null,
                    tint = Palette.textTertiary,
                )
                Text(
                    "Fully offline once installed. Not affiliated with WHOOP.",
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun IconKey() {
    Icon(
        Icons.Filled.Key,
        contentDescription = null,
        tint = Palette.accent,
    )
}

@Composable
private fun FriendsStoryCard(title: String, lines: List<String>) {
    NoopCard {
        Column(verticalArrangement = Arrangement.spacedBy(Metrics.space8)) {
            Text(title, style = NoopType.headline, color = Palette.textPrimary)
            lines.forEach { line ->
                Text(line, style = NoopType.footnote, color = Palette.textSecondary)
            }
        }
    }
}

@Composable
private fun FriendsShareToggle(
    title: String,
    blurb: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = NoopType.subhead, color = Palette.textPrimary)
            Text(blurb, style = NoopType.footnote, color = Palette.textTertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.surfaceBase,
                checkedTrackColor = Palette.accent,
                uncheckedThumbColor = Palette.textSecondary,
                uncheckedTrackColor = Palette.surfaceInset,
                uncheckedBorderColor = Palette.hairline,
            ),
        )
    }
}

private fun copyText(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

/**
 * On-device stubs for Friends network invite + share toggles.
 * Peer discovery / sync will read these; nothing leaves the phone yet.
 */
object FriendsNetworkPrefs {
    private const val PREFS = "noop_friends_network"
    private const val KEY_INVITE = "inviteCode"
    private const val KEY_FRIEND_INVITE = "friendInviteCode"
    private const val KEY_SHARE_CHARGE = "shareChargeDay"
    private const val KEY_SHARE_EFFORT = "shareEffortDay"
    private const val KEY_SHARE_REST = "shareRestNote"

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun inviteCode(c: Context): String = p(c).getString(KEY_INVITE, "") ?: ""
    fun setInviteCode(c: Context, code: String) {
        p(c).edit().putString(KEY_INVITE, code).apply()
    }

    fun friendInviteCode(c: Context): String = p(c).getString(KEY_FRIEND_INVITE, "") ?: ""
    fun setFriendInviteCode(c: Context, code: String) {
        p(c).edit().putString(KEY_FRIEND_INVITE, code.trim().uppercase()).apply()
    }

    fun shareChargeDay(c: Context): Boolean = p(c).getBoolean(KEY_SHARE_CHARGE, false)
    fun setShareChargeDay(c: Context, v: Boolean) {
        p(c).edit().putBoolean(KEY_SHARE_CHARGE, v).apply()
    }

    fun shareEffortDay(c: Context): Boolean = p(c).getBoolean(KEY_SHARE_EFFORT, false)
    fun setShareEffortDay(c: Context, v: Boolean) {
        p(c).edit().putBoolean(KEY_SHARE_EFFORT, v).apply()
    }

    fun shareRestNote(c: Context): Boolean = p(c).getBoolean(KEY_SHARE_REST, false)
    fun setShareRestNote(c: Context, v: Boolean) {
        p(c).edit().putBoolean(KEY_SHARE_REST, v).apply()
    }

    /** Short human-shareable code, e.g. NOOP-K7M2. */
    fun newInviteCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val body = buildString {
            repeat(4) { append(alphabet[Random.nextInt(alphabet.length)]) }
        }
        return "NOOP-$body"
    }
}
