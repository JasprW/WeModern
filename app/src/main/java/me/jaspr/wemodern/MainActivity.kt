package me.jaspr.wemodern

import android.Manifest
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private var setupState by mutableStateOf(SetupState())
    private var showSyncDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationChannels.ensure(this)
        getSystemService(NotificationManager::class.java).apply {
            cancel(NOTIFICATION_TEST_MESSAGE)
            cancel(NOTIFICATION_TEST_VOICE_CALL)
            cancel(NOTIFICATION_TEST_VIDEO_CALL)
        }
        setupState = readSetupState()

        setContent {
            WeModernTheme {
                WeModernApp(
                    state = setupState,
                    showSyncDialog = showSyncDialog,
                    onDismissSyncDialog = { showSyncDialog = false },
                    onOpenListenerSettings = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onRequestNotifications = { requestPostNotifications() },
                    onOpenAppSettings = {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:$packageName")
                        })
                    },
                    onShowSyncSetup = { showSyncDialog = true },
                    onPostMessageTest = { postTestNotification() },
                    onPostVoiceCallTest = { postCallTestNotification(video = false) },
                    onPostVideoCallTest = { postCallTestNotification(video = true) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupState = readSetupState()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_POST_NOTIFICATIONS) setupState = readSetupState()
    }

    private fun readSetupState() = SetupState(
        notificationListenerEnabled = isListenerEnabled(),
        postNotificationsGranted = hasPostNotificationPermission(),
        promotedNotificationsAllowed = canPostPromotedNotifications(),
        readLogsGranted = hasReadLogsPermission(),
        notificationServiceDebugEnabled = isNotificationServiceDebugEnabled(),
    )

    private fun isListenerEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(this, WeChatNotificationService::class.java)
        return enabled.split(":").any { ComponentName.unflattenFromString(it) == me }
    }

    private fun hasPostNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun canPostPromotedNotifications(): Boolean {
        return Build.VERSION.SDK_INT >= 36 && getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
    }

    private fun hasReadLogsPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED
    }

    private fun isNotificationServiceDebugEnabled(): Boolean {
        return getSystemProperty("persist.log.tag.NotificationService") == "DEBUG" ||
                getSystemProperty("log.tag.NotificationService") == "DEBUG"
    }

    private fun getSystemProperty(name: String): String {
        return try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            systemProperties.getMethod("get", String::class.java).invoke(null, name) as? String ?: ""
        } catch (error: ReflectiveOperationException) {
            Log.d(TAG, "Unable to read system property: $name", error)
            ""
        }
    }

    private fun requestPostNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !hasPostNotificationPermission()) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
        } else {
            AlertDialog.Builder(this)
                .setMessage(R.string.dialog_notifications_enabled)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun postTestNotification() {
        val notification = Notification.Builder(this, NotificationChannels.STATUS)
            .setSmallIcon(R.drawable.ic_wechat_scan_24dp)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.test_notification_text))
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_TEST_MESSAGE, notification)
    }

    private fun postCallTestNotification(video: Boolean) {
        val startedAt = System.currentTimeMillis() - 65_000L
        val iconRes = if (video) R.drawable.ic_material_videocam_24 else R.drawable.ic_material_call_24
        val builder = Notification.Builder(this, NotificationChannels.WECHAT_CALLS)
            .setSmallIcon(iconRes)
            .setContentTitle(getString(if (video) R.string.test_video_call_title else R.string.test_voice_call_title))
            .setContentText(getString(if (video) R.string.test_video_call_text else R.string.test_voice_call_text))
            .setWhen(startedAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setDefaults(Notification.DEFAULT_ALL)
            .setCategory(Notification.CATEGORY_CALL)
            .setColor(0xff33b332.toInt())
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setStyle(CallProgressStyle.create(Icon.createWithResource(this, iconRes)))
        }
        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= 36) {
            notification.extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
            Log.i(
                TAG,
                "test call promotedAllowed=${canPostPromotedNotifications()}, " +
                        "promotable=${notification.hasPromotableCharacteristics()}, video=$video"
            )
        }
        getSystemService(NotificationManager::class.java)
            .notify(if (video) NOTIFICATION_TEST_VIDEO_CALL else NOTIFICATION_TEST_VOICE_CALL, notification)
    }

    private companion object {
        const val TAG = "WeModern"
        const val REQUEST_POST_NOTIFICATIONS = 100
        const val NOTIFICATION_TEST_MESSAGE = 100
        const val NOTIFICATION_TEST_VOICE_CALL = 101
        const val NOTIFICATION_TEST_VIDEO_CALL = 102
        const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
    }
}

private data class SetupState(
    val notificationListenerEnabled: Boolean = false,
    val postNotificationsGranted: Boolean = false,
    val promotedNotificationsAllowed: Boolean = false,
    val readLogsGranted: Boolean = false,
    val notificationServiceDebugEnabled: Boolean = false,
) {
    val syncRemovalReady: Boolean
        get() = readLogsGranted && notificationServiceDebugEnabled
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeModernApp(
    state: SetupState,
    showSyncDialog: Boolean,
    onDismissSyncDialog: () -> Unit,
    onOpenListenerSettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onShowSyncSetup: () -> Unit,
    onPostMessageTest: () -> Unit,
    onPostVoiceCallTest: () -> Unit,
    onPostVideoCallTest: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Header()
            StatusPanel(state)
            PermissionsPanel(
                state = state,
                onOpenListenerSettings = onOpenListenerSettings,
                onRequestNotifications = onRequestNotifications,
                onOpenAppSettings = onOpenAppSettings,
            )
            SyncRemovalPanel(state = state, onShowSetup = onShowSyncSetup)
            TestsPanel(
                onPostMessageTest = onPostMessageTest,
                onPostVoiceCallTest = onPostVoiceCallTest,
                onPostVideoCallTest = onPostVideoCallTest,
            )
        }
    }

    if (showSyncDialog) {
        AlertDialog(
            onDismissRequest = onDismissSyncDialog,
            icon = { Icon(Icons.Rounded.Sync, contentDescription = null) },
            title = { Text(stringResource(R.string.dialog_sync_removal_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.dialog_sync_removal_message,
                        LocalContext.current.packageName,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(onClick = onDismissSyncDialog) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.subtitle),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusPanel(state: SetupState) {
    ElevatedCard(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(text = stringResource(R.string.section_status), icon = Icons.Rounded.Info)
            StatusLine(
                label = stringResource(R.string.status_listener_label),
                enabled = state.notificationListenerEnabled,
            )
            StatusLine(
                label = stringResource(R.string.status_notifications_label),
                enabled = state.postNotificationsGranted,
            )
            StatusLine(
                label = stringResource(R.string.status_live_update_label),
                enabled = state.promotedNotificationsAllowed,
                enabledText = stringResource(R.string.status_system_allowed),
                disabledText = stringResource(R.string.status_system_not_allowed),
            )
        }
    }
}

@Composable
private fun PermissionsPanel(
    state: SetupState,
    onOpenListenerSettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    ActionPanel(
        title = stringResource(R.string.section_permissions),
        icon = Icons.Rounded.Security,
    ) {
        ActionButton(
            text = stringResource(
                if (state.notificationListenerEnabled) R.string.action_listener_enabled else R.string.action_open_listener
            ),
            enabled = !state.notificationListenerEnabled,
            onClick = onOpenListenerSettings,
        )
        ActionButton(
            text = stringResource(
                if (state.postNotificationsGranted) R.string.action_notifications_enabled else R.string.action_request_notifications
            ),
            enabled = !state.postNotificationsGranted,
            onClick = onRequestNotifications,
        )
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            onClick = onOpenAppSettings,
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.action_open_app_settings))
        }
    }
}

@Composable
private fun SyncRemovalPanel(state: SetupState, onShowSetup: () -> Unit) {
    ActionPanel(
        title = stringResource(R.string.section_sync_removal),
        icon = Icons.Rounded.Sync,
    ) {
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
            ),
            headlineContent = { Text(stringResource(R.string.status_read_logs_label)) },
            supportingContent = {
                Text(statusText(state.readLogsGranted))
            },
            leadingContent = {
                StatusIcon(enabled = state.readLogsGranted)
            },
        )
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
            ),
            headlineContent = { Text(stringResource(R.string.status_notification_service_debug_label)) },
            supportingContent = {
                Text(statusText(state.notificationServiceDebugEnabled))
            },
            leadingContent = {
                StatusIcon(enabled = state.notificationServiceDebugEnabled)
            },
        )
        AssistChip(
            onClick = onShowSetup,
            leadingIcon = {
                Icon(
                    imageVector = if (state.syncRemovalReady) Icons.Rounded.CheckCircle else Icons.Rounded.Bolt,
                    contentDescription = null,
                )
            },
            label = {
                Text(
                    text = if (state.syncRemovalReady) {
                        stringResource(R.string.action_sync_removal_ready)
                    } else {
                        stringResource(R.string.action_sync_removal_setup)
                    },
                )
            },
        )
        Text(
            text = stringResource(R.string.status_reboot_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TestsPanel(
    onPostMessageTest: () -> Unit,
    onPostVoiceCallTest: () -> Unit,
    onPostVideoCallTest: () -> Unit,
) {
    ActionPanel(
        title = stringResource(R.string.section_tests),
        icon = Icons.Rounded.Notifications,
    ) {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            onClick = onPostMessageTest,
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(Icons.Rounded.Notifications, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.action_test_notification))
        }
        FilledTonalButton(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            onClick = onPostVoiceCallTest,
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(Icons.Rounded.Phone, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.action_test_voice_call))
        }
        FilledTonalButton(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            onClick = onPostVideoCallTest,
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(Icons.Rounded.Phone, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.action_test_video_call))
        }
    }
}

@Composable
private fun ActionPanel(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle(text = title, icon = icon)
            content()
        }
    }
}

@Composable
private fun SectionTitle(text: String, icon: ImageVector) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StatusLine(
    label: String,
    enabled: Boolean,
    enabledText: String = stringResource(R.string.status_enabled),
    disabledText: String = stringResource(R.string.status_disabled),
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        AssistChip(
            onClick = {},
            enabled = false,
            leadingIcon = { StatusIcon(enabled = enabled) },
            label = { Text(if (enabled) enabledText else disabledText) },
        )
    }
}

@Composable
private fun StatusIcon(enabled: Boolean) {
    Icon(
        imageVector = if (enabled) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
        contentDescription = null,
        tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ActionButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        enabled = enabled,
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(text = text)
    }
}

@Composable
private fun statusText(enabled: Boolean): String {
    return stringResource(if (enabled) R.string.status_enabled else R.string.status_disabled)
}

@Composable
private fun WeModernTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006C48),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8DF8BF),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4D6356),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE9D8),
    onSecondaryContainer = Color(0xFF0A1F15),
    tertiary = Color(0xFF3C6472),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC0E9F9),
    onTertiaryContainer = Color(0xFF001F28),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF70DBA4),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005235),
    onPrimaryContainer = Color(0xFF8DF8BF),
    secondary = Color(0xFFB3CCBC),
    onSecondary = Color(0xFF203529),
    secondaryContainer = Color(0xFF364B3E),
    onSecondaryContainer = Color(0xFFCFE9D8),
    tertiary = Color(0xFFA5CDDC),
    onTertiary = Color(0xFF073542),
    tertiaryContainer = Color(0xFF224C59),
    onTertiaryContainer = Color(0xFFC0E9F9),
)
