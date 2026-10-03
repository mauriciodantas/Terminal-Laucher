package app.lawnchair.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.components.isNotificationServiceEnabled
import app.lawnchair.ui.preferences.destinations.phosphorEntries
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.util.isDefaultLauncher
import com.android.launcher3.R
import com.patrykmichalik.opto.core.setBlocking

/**
 * First-run setup for Terminal: boot log, default launcher, phosphor color and notification
 * access. It is shown once, the first time the launcher starts, and never blocks the home screen.
 */
class OnboardingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) countOpening(this)

        setContent {
            LawnchairTheme {
                EdgeToEdge()
                OnboardingScreen(
                    onFinish = {
                        markDone(this@OnboardingActivity)
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        private const val STORE = "nostromo"
        private const val KEY_DONE = "onboarding_done"
        private const val KEY_OPENINGS = "onboarding_openings"

        /** If the user leaves midway it comes back, but only this many times. */
        private const val MAX_AUTO_OPENINGS = 3

        /** Shown on launch until it is finished or skipped, up to [MAX_AUTO_OPENINGS] times. */
        fun shouldShow(context: Context): Boolean {
            val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            return !store.getBoolean(KEY_DONE, false) && store.getInt(KEY_OPENINGS, 0) < MAX_AUTO_OPENINGS
        }

        private fun countOpening(context: Context) {
            val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            store.edit().putInt(KEY_OPENINGS, store.getInt(KEY_OPENINGS, 0) + 1).apply()
        }

        private fun markDone(context: Context) {
            context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()
        }

        fun start(context: Context) {
            // Its own task: opened on top of the launcher's task, it kept a second launcher
            // instance alive underneath when the user came back from the system home settings.
            context.startActivity(
                Intent(context, OnboardingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

private const val STEP_BOOT = 0
private const val STEP_DEFAULT = 1
private const val STEP_COLOR = 2
private const val STEP_NOTIFICATIONS = 3
private const val STEP_READY = 4

@Composable
private fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(STEP_BOOT) }
    var isDefault by remember { mutableStateOf(context.isDefaultLauncher()) }
    var notificationsGranted by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    var colorChosen by rememberSaveable { mutableStateOf(false) }
    var defaultSkipped by rememberSaveable { mutableStateOf(false) }
    var notificationsSkipped by rememberSaveable { mutableStateOf(false) }

    // The user leaves for the system settings and comes back: re-read both states.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isDefault = context.isDefaultLauncher()
        notificationsGranted = isNotificationServiceEnabled(context)
    }

    BackHandler(enabled = step > STEP_BOOT) { step -= 1 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Header()
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            when (step) {
                STEP_BOOT -> BootStep(isDefault, notificationsGranted)
                STEP_DEFAULT -> DefaultLauncherStep(isDefault)
                STEP_COLOR -> ColorStep(onChosen = { colorChosen = true })
                STEP_NOTIFICATIONS -> NotificationsStep(notificationsGranted)
                else -> ReadyStep(isDefault, defaultSkipped, notificationsGranted, notificationsSkipped, colorChosen)
            }
        }
        Spacer(Modifier.height(12.dp))
        Actions(
            step = step,
            isDefault = isDefault,
            notificationsGranted = notificationsGranted,
            onNext = { step += 1 },
            onSkip = {
                if (step == STEP_DEFAULT) defaultSkipped = true
                if (step == STEP_NOTIFICATIONS) notificationsSkipped = true
                step += 1
            },
            onFinish = onFinish,
        )
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Mono(stringResource(R.string.onboarding_header_left), color = dim(), size = 10.sp, spacing = 2.sp)
            Mono(stringResource(R.string.onboarding_header_right), color = dim(), size = 10.sp, spacing = 2.sp)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline)),
        )
    }
}

@Composable
private fun BootStep(isDefault: Boolean, notificationsGranted: Boolean) {
    val pending = (if (isDefault) 0 else 1) + (if (notificationsGranted) 0 else 1)
    Spacer(Modifier.height(8.dp))
    Mono(stringResource(R.string.onboarding_boot_title), weight = FontWeight.SemiBold, size = 13.sp, spacing = 2.sp)
    Mono(stringResource(R.string.onboarding_boot_subtitle), color = dim(), size = 10.5.sp, spacing = 1.6.sp)
    Spacer(Modifier.height(28.dp))
    Mono(stringResource(R.string.onboarding_wordmark), size = 52.sp, weight = FontWeight.Bold, spacing = 4.sp)
    Spacer(Modifier.height(28.dp))
    LogLine(stringResource(R.string.onboarding_boot_memory), stringResource(R.string.onboarding_status_ok), ok = true)
    LogLine(stringResource(R.string.onboarding_boot_icons), stringResource(R.string.onboarding_status_ok), ok = true)
    LogLine(stringResource(R.string.onboarding_boot_apps), stringResource(R.string.onboarding_status_ok), ok = true)
    LogLine(
        stringResource(R.string.onboarding_boot_default),
        stringResource(if (isDefault) R.string.onboarding_status_ok else R.string.onboarding_status_undefined),
        ok = isDefault,
    )
    LogLine(
        stringResource(R.string.onboarding_boot_notifications),
        stringResource(if (notificationsGranted) R.string.onboarding_status_ok else R.string.onboarding_status_pending),
        ok = notificationsGranted,
    )
    Spacer(Modifier.height(20.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, if (pending > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline))
            .padding(12.dp),
    ) {
        Mono(
            text = if (pending > 0) stringResource(R.string.onboarding_pending_actions, pending) else stringResource(R.string.onboarding_all_done),
            color = if (pending > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            size = 11.5.sp,
            spacing = 1.sp,
        )
    }
}

@Composable
private fun DefaultLauncherStep(isDefault: Boolean) {
    StepIntro(R.string.onboarding_default_heading, 1, R.string.onboarding_default_title)
    Mono(stringResource(R.string.set_default_launcher_tip), color = MaterialTheme.colorScheme.onSurfaceVariant, size = 12.5.sp, lineHeight = 19.sp)
    Spacer(Modifier.height(18.dp))
    Panel {
        Mono(stringResource(R.string.onboarding_default_unlocks), color = dim(), size = 10.sp, spacing = 1.8.sp)
        Spacer(Modifier.height(8.dp))
        Mono(stringResource(R.string.onboarding_default_benefit_home), size = 12.sp)
        Spacer(Modifier.height(6.dp))
        Mono(stringResource(R.string.onboarding_default_benefit_shortcuts), size = 12.sp)
    }
    if (isDefault) {
        Spacer(Modifier.height(18.dp))
        Panel(highlight = true) {
            Mono("✓  " + stringResource(R.string.onboarding_default_active), weight = FontWeight.SemiBold, size = 13.sp, spacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Mono(stringResource(R.string.onboarding_default_active_desc), color = dim(), size = 11.sp)
        }
    }
}

@Composable
private fun ColorStep(onChosen: () -> Unit) {
    val prefs2 = preferenceManager2()
    val accent by prefs2.accentColor.asState()
    StepIntro(R.string.onboarding_color_heading, 2, R.string.onboarding_color_title)
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        phosphorEntries.forEach { entry ->
            val option = entry.value as ColorOption.CustomColor
            val label = entry.label()
            val selected = (accent as? ColorOption.CustomColor)?.color == option.color
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(option.color))
                    .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .semantics {
                        role = Role.Button
                        contentDescription = label
                    }
                    .clickable {
                        prefs2.accentColor.setBlocking(option)
                        onChosen()
                    },
            )
        }
    }
    Spacer(Modifier.height(20.dp))
    Mono(stringResource(R.string.onboarding_color_hint), color = dim(), size = 11.sp, lineHeight = 17.sp)
}

@Composable
private fun NotificationsStep(granted: Boolean) {
    StepIntro(R.string.onboarding_notifications_heading, 3, R.string.onboarding_notifications_title)
    Mono(stringResource(R.string.onboarding_notifications_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, size = 12.5.sp, lineHeight = 19.sp)
    if (granted) {
        Spacer(Modifier.height(20.dp))
        Panel(highlight = true) {
            Mono("✓  " + stringResource(R.string.onboarding_notifications_granted), weight = FontWeight.SemiBold, size = 13.sp, spacing = 1.sp)
        }
    }
}

@Composable
private fun ReadyStep(
    isDefault: Boolean,
    defaultSkipped: Boolean,
    notificationsGranted: Boolean,
    notificationsSkipped: Boolean,
    colorChosen: Boolean,
) {
    Spacer(Modifier.height(8.dp))
    Mono(stringResource(R.string.onboarding_ready_heading), weight = FontWeight.SemiBold, size = 13.sp, spacing = 2.sp)
    Spacer(Modifier.height(24.dp))
    Mono(stringResource(R.string.onboarding_ready_title), size = 44.sp, weight = FontWeight.Bold, spacing = 3.sp)
    Spacer(Modifier.height(20.dp))
    LogLine(
        stringResource(R.string.onboarding_boot_default),
        stringResource(if (isDefault) R.string.onboarding_status_ok else R.string.onboarding_status_skipped),
        ok = isDefault,
        warn = !isDefault && defaultSkipped,
    )
    LogLine(
        stringResource(R.string.onboarding_ready_color),
        stringResource(R.string.onboarding_status_ok),
        ok = true,
    )
    LogLine(
        stringResource(R.string.onboarding_boot_notifications),
        stringResource(if (notificationsGranted) R.string.onboarding_status_ok else R.string.onboarding_status_skipped),
        ok = notificationsGranted,
        warn = !notificationsGranted && notificationsSkipped,
    )
    if (!isDefault || !notificationsGranted) {
        Spacer(Modifier.height(14.dp))
        Mono(stringResource(R.string.onboarding_ready_skipped), color = dim(), size = 11.sp, lineHeight = 17.sp)
    }
    Spacer(Modifier.height(20.dp))
    Mono(stringResource(R.string.onboarding_ready_hint_swipe), color = MaterialTheme.colorScheme.onSurfaceVariant, size = 11.5.sp, lineHeight = 20.sp)
    Mono(stringResource(R.string.onboarding_ready_hint_command), color = MaterialTheme.colorScheme.onSurfaceVariant, size = 11.5.sp, lineHeight = 20.sp)
}

@Composable
private fun Actions(
    step: Int,
    isDefault: Boolean,
    notificationsGranted: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (step) {
            STEP_BOOT -> {
                PrimaryButton(stringResource(R.string.onboarding_start), onNext)
                GhostButton(stringResource(R.string.onboarding_skip_all), onFinish)
            }

            STEP_DEFAULT -> if (isDefault) {
                PrimaryButton(stringResource(R.string.onboarding_continue), onNext)
            } else {
                PrimaryButton(
                    text = stringResource(R.string.set_default_launcher_action),
                    onClick = { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) },
                    danger = true,
                )
                GhostButton(stringResource(R.string.onboarding_skip), onSkip)
            }

            STEP_COLOR -> PrimaryButton(stringResource(R.string.onboarding_continue), onNext)

            STEP_NOTIFICATIONS -> if (notificationsGranted) {
                PrimaryButton(stringResource(R.string.onboarding_continue), onNext)
            } else {
                PrimaryButton(
                    text = stringResource(R.string.onboarding_notifications_grant),
                    onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                )
                GhostButton(stringResource(R.string.onboarding_skip), onSkip)
            }

            else -> PrimaryButton(stringResource(R.string.onboarding_enter), onFinish)
        }
    }
}

@Composable
private fun StepIntro(headingRes: Int, number: Int, titleRes: Int) {
    Spacer(Modifier.height(8.dp))
    Mono(stringResource(headingRes), weight = FontWeight.SemiBold, size = 13.sp, spacing = 2.sp)
    Spacer(Modifier.height(10.dp))
    Mono(stringResource(R.string.onboarding_step_of, number), color = dim(), size = 11.sp, spacing = 2.sp)
    Spacer(Modifier.height(14.dp))
    Mono(stringResource(titleRes), color = MaterialTheme.colorScheme.primary, size = 19.sp, weight = FontWeight.SemiBold, lineHeight = 25.sp)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun LogLine(label: String, status: String, ok: Boolean, warn: Boolean = false) {
    val statusColor = when {
        ok -> MaterialTheme.colorScheme.primary
        warn -> dim()
        else -> MaterialTheme.colorScheme.error
    }
    Row(modifier = Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Mono("> $label", size = 12.sp, spacing = 0.8.sp)
        Mono("·".repeat(40), color = dim(), size = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
        Mono(status, color = statusColor, size = 12.sp)
    }
}

@Composable
private fun Panel(highlight: Boolean = false, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
            .padding(12.dp),
    ) { content() }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, danger: Boolean = false) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = MaterialTheme.shapes.extraSmall,
        colors = if (danger) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) { Mono(text, color = Color.Unspecified, weight = FontWeight.SemiBold, size = 13.sp, spacing = 2.sp) }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) { Mono(text, color = Color.Unspecified, size = 11.5.sp, spacing = 1.sp) }
}

@Composable
private fun dim(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)

@Composable
private fun Mono(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: TextUnit = 12.sp,
    weight: FontWeight = FontWeight.Normal,
    spacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = size,
        fontWeight = weight,
        letterSpacing = spacing,
        lineHeight = lineHeight,
        maxLines = maxLines,
    )
}
