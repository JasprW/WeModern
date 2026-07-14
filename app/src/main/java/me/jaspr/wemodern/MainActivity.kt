package me.jaspr.wemodern

import android.Manifest
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var setupState by mutableStateOf(SetupState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationChannels.ensure(this)
        ConversationShortcuts.ensureSettingsShortcut(this)
        getSystemService(NotificationManager::class.java).apply {
            cancel(MessageTestNotifications.CURRENT_ID)
            cancel(CallTestNotifications.CURRENT_ID)
            cancel(CallTestNotifications.LEGACY_VIDEO_ID)
        }
        MessageTestNotifications.removeConversationShortcut(this)
        ConversationBubbleStore.remove(MessageTestNotifications.SHORTCUT_ID)
        setupState = readSetupState()

        setContent {
            WeModernTheme {
                WeModernApp(
                    state = setupState,
                    syncCommands = getString(R.string.sync_commands, packageName),
                    onOpenListenerSettings = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onRequestNotifications = { requestPostNotifications() },
                    onOpenChatBubbleSettings = { openChatBubbleSettings() },
                    onOpenPromotedNotificationSettings = { openPromotedNotificationSettings() },
                    onRequestIgnoreBatteryOptimization = { requestIgnoreBatteryOptimization() },
                    onOpenAppSettings = { openAppSettings() },
                    onSetAppIconOpensWeChat = { enabled ->
                        AppIconBehavior.setOpenWeChatEnabled(this, enabled)
                        setupState = readSetupState()
                    },
                    onSetBubbleTrampolineEnabled = { enabled ->
                        BubbleTrampolineBehavior.setEnabled(
                            this,
                            enabled && areChatBubblesAllowed(),
                        )
                        setupState = readSetupState()
                    },
                    onCopySyncCommands = { copySyncCommands() },
                    onPostMessageTest = { postTestNotification() },
                    onPostVoiceCallTest = { postCallTestNotification(video = false) },
                    onPostVideoCallTest = { postCallTestNotification(video = true) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val launchedFromBubble = AppIconLaunchPolicy.isLaunchedFromBubble(this)
        val openWeChatEnabled = launchedFromBubble && AppIconBehavior.shouldOpenWeChat(this)
        if (AppIconLaunchPolicy.shouldForwardFromSettings(
                launchedFromBubble,
                openWeChatEnabled,
            ) && WeChatLauncher.open(this)
        ) {
            finish()
            return
        }
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

    private fun readSetupState(): SetupState {
        val notificationListenerEnabled = isListenerEnabled()
        val postNotificationsGranted = hasPostNotificationPermission()
        val chatBubblesAllowed = areChatBubblesAllowed()
        if ((!notificationListenerEnabled || !postNotificationsGranted) &&
            AppIconBehavior.isOpenWeChatEnabled(this)
        ) {
            AppIconBehavior.setOpenWeChatEnabled(this, false)
        }
        if (!chatBubblesAllowed && BubbleTrampolineBehavior.isEnabled(this)) {
            BubbleTrampolineBehavior.setEnabled(this, false)
        }
        return SetupState(
            notificationListenerEnabled = notificationListenerEnabled,
            postNotificationsGranted = postNotificationsGranted,
            appIconOpensWeChat = AppIconBehavior.isOpenWeChatEnabled(this),
            bubbleTrampolineEnabled = BubbleTrampolineBehavior.isEnabled(this),
            chatBubblesAllowed = chatBubblesAllowed,
            promotedNotificationsAllowed = canPostPromotedNotifications(),
            batteryOptimizationIgnored = isBatteryOptimizationIgnored(),
            readLogsGranted = hasReadLogsPermission(),
            notificationServiceDebugEnabled = isNotificationServiceDebugEnabled(),
        )
    }

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

    @Suppress("DEPRECATION")
    private fun areChatBubblesAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val manager = getSystemService(NotificationManager::class.java)
        return if (Build.VERSION.SDK_INT >= 31) {
            manager.areBubblesEnabled() &&
                    manager.bubblePreference != NotificationManager.BUBBLE_PREFERENCE_NONE
        } else {
            manager.areBubblesAllowed()
        }
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        return getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
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

    private fun requestIgnoreBatteryOptimization() {
        if (isBatteryOptimizationIgnored()) {
            AlertDialog.Builder(this)
                .setMessage(R.string.dialog_battery_optimization_ignored)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching {
            startActivity(requestIntent)
        }.onFailure {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun openPromotedNotificationSettings() {
        if (Build.VERSION.SDK_INT < 36) return
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        }.onFailure {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            }.onFailure {
                openAppSettings()
            }
        }
    }

    private fun openChatBubbleSettings() {
        if (Build.VERSION.SDK_INT < 29) return
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        }.onFailure {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            }.onFailure {
                openAppSettings()
            }
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        })
    }

    private fun copySyncCommands() {
        val commands = getString(R.string.sync_commands, packageName)
        getSystemService(ClipboardManager::class.java).setPrimaryClip(
            ClipData.newPlainText(getString(R.string.sync_commands_clip_label), commands)
        )
    }

    private fun postTestNotification() {
        val now = System.currentTimeMillis()
        val senderAvatar = Icon.createWithResource(this, R.drawable.ic_test_message_avatar_48)
        val style = if (Build.VERSION.SDK_INT >= 28) {
            val sender = android.app.Person.Builder()
                .setName(getString(R.string.test_message_sender))
                .setIcon(senderAvatar)
                .build()
            val me = android.app.Person.Builder()
                .setName(getString(R.string.app_name))
                .build()
            Notification.MessagingStyle(me)
                .setConversationTitle(getString(R.string.test_message_sender))
                .addMessage(
                    Notification.MessagingStyle.Message(
                        getString(R.string.test_message_short),
                        now - TEST_MESSAGE_SHORT_AGE_MS,
                        sender,
                    )
                )
                .addMessage(
                    Notification.MessagingStyle.Message(
                        getString(R.string.test_message_long),
                        now,
                        sender,
                    )
                )
        } else {
            @Suppress("DEPRECATION")
            Notification.MessagingStyle(getString(R.string.app_name))
                .setConversationTitle(getString(R.string.test_message_sender))
                .addMessage(
                    getString(R.string.test_message_short),
                    now - TEST_MESSAGE_SHORT_AGE_MS,
                    getString(R.string.test_message_sender),
                )
                .addMessage(
                    getString(R.string.test_message_long),
                    now,
                    getString(R.string.test_message_sender),
                )
        }
        val smallIcon = WeChatNotificationService.lastCapturedWeChatSmallIcon()
            ?: Icon.createWithResource(this, R.drawable.ic_wechat_notification_fallback)
        MessageTestNotifications.removeConversationShortcut(this)
        MessageTestNotifications.publishConversationShortcut(
            this,
            getString(R.string.test_message_sender),
            senderAvatar,
        )
        val contentIntent = PendingIntent.getActivity(
            this,
            MessageTestNotifications.CURRENT_ID,
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val bubbleState = ConversationBubbleState.create(
            MessageTestNotifications.SHORTCUT_ID,
            getString(R.string.test_message_sender),
            contentIntent,
        ).append(
            getString(R.string.test_message_sender),
            getString(R.string.test_message_short),
            now - TEST_MESSAGE_SHORT_AGE_MS,
            contentIntent,
        ).append(
            getString(R.string.test_message_sender),
            getString(R.string.test_message_long),
            now,
            contentIntent,
        )
        ConversationBubbleStore.update(bubbleState)
        val builder = Notification.Builder(this, NotificationChannels.WECHAT_MESSAGES)
            .setSmallIcon(smallIcon)
            .setLargeIcon(senderAvatar)
            .setContentTitle(getString(R.string.test_message_sender))
            .setContentText(getString(R.string.test_message_long))
            .setStyle(style)
            .setWhen(now)
            .setShowWhen(true)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setColor(0xff33b332.toInt())
            .setContentIntent(contentIntent)
            .setShortcutId(MessageTestNotifications.SHORTCUT_ID)
        ConversationBubbles.applyTo(this, builder, bubbleState, senderAvatar)
        val notification = builder.build()
        getSystemService(NotificationManager::class.java)
            .notify(MessageTestNotifications.CURRENT_ID, notification)
    }

    private fun postCallTestNotification(video: Boolean) {
        val startedAt = System.currentTimeMillis()
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
            .setCategory(Notification.CATEGORY_CALL)
            .setColor(0xff33b332.toInt())
        val notification = CallProgressStyle.build(
            builder,
            Icon.createWithResource(this, iconRes),
        )
        if (Build.VERSION.SDK_INT >= 36) {
            Log.i(
                TAG,
                "test call promotedAllowed=${canPostPromotedNotifications()}, " +
                        "promotable=${notification.hasPromotableCharacteristics()}, video=$video"
            )
        }
        getSystemService(NotificationManager::class.java)
            .notify(CallTestNotifications.idFor(video), notification)
    }

    private companion object {
        const val TAG = "WeModern"
        const val REQUEST_POST_NOTIFICATIONS = 100
        const val TEST_MESSAGE_SHORT_AGE_MS = 90_000L
    }
}

private data class SetupState(
    val notificationListenerEnabled: Boolean = false,
    val postNotificationsGranted: Boolean = false,
    val appIconOpensWeChat: Boolean = false,
    val bubbleTrampolineEnabled: Boolean = false,
    val chatBubblesAllowed: Boolean = false,
    val promotedNotificationsAllowed: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val readLogsGranted: Boolean = false,
    val notificationServiceDebugEnabled: Boolean = false,
) {
    val coreReady: Boolean
        get() = notificationListenerEnabled && postNotificationsGranted

    val requiredStepsRemaining: Int
        get() = listOf(notificationListenerEnabled, postNotificationsGranted).count { !it }

    val nextRequiredStep: RequiredSetupStep?
        get() = when {
            !notificationListenerEnabled -> RequiredSetupStep.NotificationAccess
            !postNotificationsGranted -> RequiredSetupStep.PostNotifications
            else -> null
        }

    val liveUpdatesAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= 36

    val chatBubblesAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= 29

    val bubbleTrampolineAvailable: Boolean
        get() = BubbleTrampolineBehavior.isSupported(Build.VERSION.SDK_INT)

    val syncRemovalReady: Boolean
        get() = readLogsGranted && notificationServiceDebugEnabled
}

private enum class RequiredSetupStep {
    NotificationAccess,
    PostNotifications,
}

private enum class SetupStatusTone {
    Success,
    Attention,
    Neutral,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeModernApp(
    state: SetupState,
    syncCommands: String,
    onOpenListenerSettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenChatBubbleSettings: () -> Unit,
    onOpenPromotedNotificationSettings: () -> Unit,
    onRequestIgnoreBatteryOptimization: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onSetAppIconOpensWeChat: (Boolean) -> Unit,
    onSetBubbleTrampolineEnabled: (Boolean) -> Unit,
    onCopySyncCommands: () -> Unit,
    onPostMessageTest: () -> Unit,
    onPostVoiceCallTest: () -> Unit,
    onPostVideoCallTest: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val messageSentText = stringResource(R.string.snackbar_test_message_sent)
    val voiceSentText = stringResource(R.string.snackbar_test_voice_sent)
    val videoSentText = stringResource(R.string.snackbar_test_video_sent)
    val commandsCopiedText = stringResource(R.string.snackbar_sync_commands_copied)
    var syncExpanded by remember { mutableStateOf(false) }
    var chatBubblesExpanded by remember { mutableStateOf(false) }
    var showAppIconShortcutTip by remember { mutableStateOf(false) }
    val appIconShortcutTipSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dismissAppIconShortcutTip: () -> Unit = {
        coroutineScope.launch {
            appIconShortcutTipSheetState.hide()
        }.invokeOnCompletion {
            if (!appIconShortcutTipSheetState.isVisible) {
                showAppIconShortcutTip = false
            }
        }
        Unit
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = GoogleSansFlexDisplay,
                        fontWeight = FontWeight.Normal,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenAppSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.action_open_app_settings),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                item {
                    SetupHero(
                        state = state,
                        onOpenListenerSettings = onOpenListenerSettings,
                        onRequestNotifications = onRequestNotifications,
                    )
                }
                item {
                    SettingsSection(
                        state = state,
                        onOpenListenerSettings = onOpenListenerSettings,
                        onRequestNotifications = onRequestNotifications,
                        onOpenPromotedNotificationSettings = onOpenPromotedNotificationSettings,
                        onRequestIgnoreBatteryOptimization = onRequestIgnoreBatteryOptimization,
                    )
                }
                item {
                    AdvancedSection(
                        state = state,
                        chatBubblesExpanded = chatBubblesExpanded,
                        syncExpanded = syncExpanded,
                        syncCommands = syncCommands,
                        onSetAppIconOpensWeChat = { enabled ->
                            onSetAppIconOpensWeChat(enabled)
                            if (enabled) showAppIconShortcutTip = true
                        },
                        onSetBubbleTrampolineEnabled = onSetBubbleTrampolineEnabled,
                        onToggleChatBubblesExpanded = {
                            chatBubblesExpanded = !chatBubblesExpanded
                        },
                        onOpenChatBubbleSettings = onOpenChatBubbleSettings,
                        onToggleSyncExpanded = { syncExpanded = !syncExpanded },
                        onCopySyncCommands = {
                            onCopySyncCommands()
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(commandsCopiedText)
                            }
                        },
                    )
                }
                item {
                    TestsSection(
                        enabled = state.coreReady,
                        onPostMessageTest = {
                            onPostMessageTest()
                            coroutineScope.launch { snackbarHostState.showSnackbar(messageSentText) }
                        },
                        onPostVoiceCallTest = {
                            onPostVoiceCallTest()
                            coroutineScope.launch { snackbarHostState.showSnackbar(voiceSentText) }
                        },
                        onPostVideoCallTest = {
                            onPostVideoCallTest()
                            coroutineScope.launch { snackbarHostState.showSnackbar(videoSentText) }
                        },
                    )
                }
            }
        }
    }

    if (showAppIconShortcutTip) {
        ModalBottomSheet(
            onDismissRequest = dismissAppIconShortcutTip,
            sheetState = appIconShortcutTipSheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.app_icon_shortcut_tip_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.app_icon_shortcut_tip_message),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = dismissAppIconShortcutTip,
                ) {
                    Text(stringResource(R.string.action_got_it))
                }
            }
        }
    }
}

@Composable
private fun SetupHero(
    state: SetupState,
    onOpenListenerSettings: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val isReady = state.coreReady
    val containerColor = if (isReady) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = if (isReady) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }
    val accentColor = if (isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val accentContentColor = if (isReady) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiary

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = 32.dp,
            topEnd = 32.dp,
            bottomEnd = 12.dp,
            bottomStart = 32.dp,
        ),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = accentColor,
                    contentColor = accentContentColor,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isReady) Icons.Rounded.Check else Icons.Rounded.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(
                            if (isReady) R.string.setup_state_running else R.string.setup_state_required
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor.copy(alpha = 0.72f),
                    )
                    Text(
                        text = if (isReady) {
                            stringResource(R.string.setup_ready_title)
                        } else {
                            pluralStringResource(
                                R.plurals.setup_remaining_steps,
                                state.requiredStepsRemaining,
                                state.requiredStepsRemaining,
                            )
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = GoogleSansFlexDisplay,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }

            Text(
                text = stringResource(
                    if (isReady) R.string.setup_ready_message else R.string.setup_remaining_message
                ),
                style = MaterialTheme.typography.bodyLarge,
            )

            if (isReady) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CapabilityPill(text = stringResource(R.string.capability_notification_rewrite))
                    if (state.promotedNotificationsAllowed) {
                        CapabilityPill(text = stringResource(R.string.status_live_update_label))
                    }
                    if (state.chatBubblesAllowed) {
                        CapabilityPill(text = stringResource(R.string.chat_bubbles_title))
                    }
                }
            } else {
                val nextStep = state.nextRequiredStep
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    onClick = when (nextStep) {
                        RequiredSetupStep.NotificationAccess -> onOpenListenerSettings
                        RequiredSetupStep.PostNotifications -> onRequestNotifications
                        null -> ({})
                    },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Icon(
                        imageVector = if (nextStep == RequiredSetupStep.NotificationAccess) {
                            Icons.Rounded.Security
                        } else {
                            Icons.Rounded.NotificationsActive
                        },
                        contentDescription = null,
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = stringResource(
                            if (nextStep == RequiredSetupStep.NotificationAccess) {
                                R.string.action_open_listener
                            } else {
                                R.string.action_request_notifications
                            }
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilityPill(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SettingsSection(
    state: SetupState,
    onOpenListenerSettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenPromotedNotificationSettings: () -> Unit,
    onRequestIgnoreBatteryOptimization: () -> Unit,
) {
    val firstShape = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomStart = 12.dp,
        bottomEnd = 12.dp,
    )
    val middleShape = RoundedCornerShape(12.dp)
    val lastShape = RoundedCornerShape(
        topStart = 12.dp,
        topEnd = 12.dp,
        bottomStart = 28.dp,
        bottomEnd = 28.dp,
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = stringResource(R.string.section_setup))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SettingRow(
                title = stringResource(R.string.setup_notification_access_title),
                supporting = stringResource(R.string.setup_notification_access_description),
                icon = Icons.Rounded.Security,
                status = stringResource(
                    if (state.notificationListenerEnabled) R.string.setup_status_enabled else R.string.setup_status_pending
                ),
                statusTone = if (state.notificationListenerEnabled) SetupStatusTone.Success else SetupStatusTone.Attention,
                highlighted = state.nextRequiredStep == RequiredSetupStep.NotificationAccess,
                shape = firstShape,
                onClick = if (state.notificationListenerEnabled) null else onOpenListenerSettings,
            )
            SettingRow(
                title = stringResource(R.string.setup_post_notifications_title),
                supporting = stringResource(R.string.setup_post_notifications_description),
                icon = Icons.Rounded.NotificationsActive,
                status = stringResource(
                    if (state.postNotificationsGranted) R.string.setup_status_enabled else R.string.setup_status_pending
                ),
                statusTone = if (state.postNotificationsGranted) SetupStatusTone.Success else SetupStatusTone.Attention,
                highlighted = state.nextRequiredStep == RequiredSetupStep.PostNotifications,
                shape = middleShape,
                onClick = if (state.postNotificationsGranted) null else onRequestNotifications,
            )
            if (state.liveUpdatesAvailable) {
                val liveUpdateBlocked = !state.postNotificationsGranted
                SettingRow(
                    title = stringResource(R.string.status_live_update_label),
                    supporting = stringResource(R.string.setup_live_update_description),
                    icon = Icons.Rounded.Timeline,
                    status = stringResource(
                        when {
                            state.promotedNotificationsAllowed -> R.string.setup_status_enabled
                            liveUpdateBlocked -> R.string.setup_status_requires_notifications
                            else -> R.string.setup_status_recommended
                        }
                    ),
                    statusTone = when {
                        state.promotedNotificationsAllowed -> SetupStatusTone.Success
                        liveUpdateBlocked -> SetupStatusTone.Neutral
                        else -> SetupStatusTone.Attention
                    },
                    highlighted = false,
                    shape = middleShape,
                    onClick = if (state.promotedNotificationsAllowed || liveUpdateBlocked) {
                        null
                    } else {
                        onOpenPromotedNotificationSettings
                    },
                )
            }
            SettingRow(
                title = stringResource(R.string.setup_battery_title),
                supporting = stringResource(R.string.setup_battery_description),
                icon = Icons.Rounded.BatterySaver,
                status = stringResource(
                    if (state.batteryOptimizationIgnored) R.string.setup_status_enabled else R.string.setup_status_recommended
                ),
                statusTone = if (state.batteryOptimizationIgnored) SetupStatusTone.Success else SetupStatusTone.Attention,
                highlighted = false,
                shape = lastShape,
                onClick = if (state.batteryOptimizationIgnored) null else onRequestIgnoreBatteryOptimization,
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    supporting: String,
    icon: ImageVector,
    status: String,
    statusTone: SetupStatusTone,
    highlighted: Boolean,
    shape: Shape,
    onClick: (() -> Unit)?,
) {
    val containerColor = if (highlighted) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val modifier = Modifier
        .fillMaxWidth()
        .semantics(mergeDescendants = true) {}
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(17.dp),
                color = if (highlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (highlighted) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            SetupStatusLabel(
                text = status,
                tone = statusTone,
                actionable = onClick != null,
            )
        }
    }

    if (onClick != null) {
        Surface(
            modifier = modifier,
            onClick = onClick,
            shape = shape,
            color = containerColor,
        ) {
            rowContent()
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor,
        ) {
            rowContent()
        }
    }
}

@Composable
private fun SetupStatusLabel(
    text: String,
    tone: SetupStatusTone,
    actionable: Boolean,
) {
    val tint = when (tone) {
        SetupStatusTone.Success -> MaterialTheme.colorScheme.primary
        SetupStatusTone.Attention -> MaterialTheme.colorScheme.tertiary
        SetupStatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tone == SetupStatusTone.Success) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = tint,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
        )
        if (actionable) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = tint,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, supporting: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = GoogleSansFlexDisplay,
            fontWeight = FontWeight.Normal,
        )
        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AdvancedSection(
    state: SetupState,
    chatBubblesExpanded: Boolean,
    syncExpanded: Boolean,
    syncCommands: String,
    onSetAppIconOpensWeChat: (Boolean) -> Unit,
    onSetBubbleTrampolineEnabled: (Boolean) -> Unit,
    onToggleChatBubblesExpanded: () -> Unit,
    onOpenChatBubbleSettings: () -> Unit,
    onToggleSyncExpanded: () -> Unit,
    onCopySyncCommands: () -> Unit,
) {
    val firstShape = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomStart = 12.dp,
        bottomEnd = 12.dp,
    )
    val lastShape = RoundedCornerShape(
        topStart = 12.dp,
        topEnd = 12.dp,
        bottomStart = 28.dp,
        bottomEnd = 28.dp,
    )
    val middleShape = RoundedCornerShape(12.dp)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = stringResource(R.string.section_advanced))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = firstShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = state.appIconOpensWeChat,
                            enabled = state.coreReady,
                            role = Role.Switch,
                            onValueChange = onSetAppIconOpensWeChat,
                        )
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(17.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.TouchApp,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.app_icon_open_wechat_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(
                                if (state.coreReady) {
                                    R.string.app_icon_open_wechat_description
                                } else {
                                    R.string.app_icon_open_wechat_locked_description
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    Switch(
                        checked = state.appIconOpensWeChat,
                        enabled = state.coreReady,
                        onCheckedChange = null,
                    )
                }
            }
            if (state.chatBubblesAvailable) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = middleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onToggleChatBubblesExpanded)
                                .semantics(mergeDescendants = true) {}
                                .padding(horizontal = 20.dp, vertical = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = RoundedCornerShape(17.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChatBubble,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.chat_bubbles_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.chat_bubbles_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = stringResource(
                                    if (state.chatBubblesAllowed) {
                                        R.string.setup_status_enabled
                                    } else {
                                        R.string.setup_status_recommended
                                    }
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (state.chatBubblesAllowed) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                },
                            )
                            Icon(
                                imageVector = if (chatBubblesExpanded) {
                                    Icons.Rounded.ExpandLess
                                } else {
                                    Icons.Rounded.ExpandMore
                                },
                                contentDescription = stringResource(
                                    if (chatBubblesExpanded) {
                                        R.string.action_collapse
                                    } else {
                                        R.string.action_expand
                                    }
                                ),
                            )
                        }

                        AnimatedVisibility(
                            visible = chatBubblesExpanded,
                            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                        ) {
                            Column {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.chat_bubbles_explanation),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.Top,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Info,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                modifier = Modifier.weight(1f),
                                                text = stringResource(R.string.chat_bubbles_setup_guidance),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    if (state.bubbleTrampolineAvailable) {
                                        val trampolineCanBeSet = state.coreReady &&
                                                state.chatBubblesAllowed
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(18.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .toggleable(
                                                        value = state.bubbleTrampolineEnabled,
                                                        enabled = trampolineCanBeSet,
                                                        role = Role.Switch,
                                                        onValueChange = onSetBubbleTrampolineEnabled,
                                                    )
                                                    .semantics(mergeDescendants = true) {}
                                                    .padding(16.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                                    contentDescription = null,
                                                    tint = if (trampolineCanBeSet) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                )
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                                ) {
                                                    Text(
                                                        text = stringResource(
                                                            R.string.bubble_trampoline_title
                                                        ),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                    )
                                                    Text(
                                                        text = stringResource(
                                                            when {
                                                                !state.chatBubblesAllowed -> {
                                                                    R.string.bubble_trampoline_bubbles_required_description
                                                                }
                                                                !state.coreReady -> {
                                                                    R.string.bubble_trampoline_locked_description
                                                                }
                                                                else -> {
                                                                    R.string.bubble_trampoline_description
                                                                }
                                                            }
                                                        ),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                                Switch(
                                                    checked = state.bubbleTrampolineEnabled,
                                                    enabled = trampolineCanBeSet,
                                                    onCheckedChange = null,
                                                )
                                            }
                                        }
                                    }
                                    Button(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = onOpenChatBubbleSettings,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Settings,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.size(8.dp))
                                        Text(stringResource(R.string.action_open_chat_bubble_settings))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = lastShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleSyncExpanded)
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(17.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.sync_removal_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.sync_removal_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(
                            if (state.syncRemovalReady) R.string.setup_status_ready else R.string.setup_status_needs_adb
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (state.syncRemovalReady) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                    )
                    Icon(
                        imageVector = if (syncExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = stringResource(
                            if (syncExpanded) R.string.action_collapse else R.string.action_expand
                        ),
                    )
                }

                AnimatedVisibility(
                    visible = syncExpanded,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    Column {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            AdvancedConditionRow(
                                title = stringResource(R.string.status_read_logs_label),
                                enabled = state.readLogsGranted,
                            )
                            AdvancedConditionRow(
                                title = stringResource(R.string.status_notification_service_debug_label),
                                enabled = state.notificationServiceDebugEnabled,
                            )
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = syncCommands,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        TextButton(onClick = onCopySyncCommands) {
                                            Icon(
                                                imageVector = Icons.Rounded.ContentCopy,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.size(8.dp))
                                            Text(stringResource(R.string.action_copy_commands))
                                        }
                                    }
                                }
                            }
                            Text(
                                text = stringResource(R.string.status_reboot_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun AdvancedConditionRow(title: String, enabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (enabled) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(if (enabled) R.string.setup_status_enabled else R.string.setup_status_missing),
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TestsSection(
    enabled: Boolean,
    onPostMessageTest: () -> Unit,
    onPostVoiceCallTest: () -> Unit,
    onPostVideoCallTest: () -> Unit,
) {
    val messageInteraction = remember { MutableInteractionSource() }
    val voiceInteraction = remember { MutableInteractionSource() }
    val videoInteraction = remember { MutableInteractionSource() }
    val messagePressed by messageInteraction.collectIsPressedAsState()
    val voicePressed by voiceInteraction.collectIsPressedAsState()
    val videoPressed by videoInteraction.collectIsPressedAsState()
    val anyPressed = messagePressed || voicePressed || videoPressed

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = stringResource(R.string.section_tests),
            supporting = stringResource(
                if (enabled) R.string.tests_support_ready else R.string.tests_support_locked
            ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TestActionButton(
                modifier = Modifier.weight(testButtonGroupWeight(messagePressed, anyPressed)),
                text = stringResource(R.string.test_short_message),
                icon = Icons.Rounded.Notifications,
                enabled = enabled,
                interactionSource = messageInteraction,
                onClick = onPostMessageTest,
            )
            TestActionButton(
                modifier = Modifier.weight(testButtonGroupWeight(voicePressed, anyPressed)),
                text = stringResource(R.string.test_short_voice),
                icon = Icons.Rounded.PhoneInTalk,
                enabled = enabled,
                interactionSource = voiceInteraction,
                onClick = onPostVoiceCallTest,
            )
            TestActionButton(
                modifier = Modifier.weight(testButtonGroupWeight(videoPressed, anyPressed)),
                text = stringResource(R.string.test_short_video),
                icon = Icons.Rounded.Videocam,
                enabled = enabled,
                interactionSource = videoInteraction,
                onClick = onPostVideoCallTest,
            )
        }
    }
}

@Composable
private fun testButtonGroupWeight(pressed: Boolean, anyPressed: Boolean): Float {
    return animateFloatAsState(
        targetValue = when {
            pressed -> 1.16f
            anyPressed -> 0.92f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "testButtonGroupWeight",
    ).value
}

@Composable
private fun TestActionButton(
    modifier: Modifier,
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        modifier = modifier.fillMaxHeight(),
        enabled = enabled,
        onClick = onClick,
        interactionSource = interactionSource,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
internal fun WeModernTheme(content: @Composable () -> Unit) {
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
        typography = WeModernTypography,
        content = content,
    )
}

@OptIn(ExperimentalTextApi::class)
private val GoogleSansFlexRounded = FontFamily(
    Font(
        R.font.google_sans_flex_variable,
        variationSettings = FontVariation.Settings(
            FontVariation.Setting("ROND", 100f),
        ),
    ),
)

@OptIn(ExperimentalTextApi::class)
private val GoogleSansFlexDisplay = FontFamily(
    Font(
        R.font.google_sans_flex_variable,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(760),
            FontVariation.width(112f),
            FontVariation.opticalSizing(32.sp),
            FontVariation.Setting("ROND", 100f),
        ),
    ),
)

private val DefaultWeModernTypography = Typography()

private val WeModernTypography = Typography(
    displayLarge = DefaultWeModernTypography.displayLarge.roundedGoogleSansFlex(),
    displayMedium = DefaultWeModernTypography.displayMedium.roundedGoogleSansFlex(),
    displaySmall = DefaultWeModernTypography.displaySmall.roundedGoogleSansFlex(),
    headlineLarge = DefaultWeModernTypography.headlineLarge.roundedGoogleSansFlex(),
    headlineMedium = DefaultWeModernTypography.headlineMedium.roundedGoogleSansFlex(),
    headlineSmall = DefaultWeModernTypography.headlineSmall.roundedGoogleSansFlex(),
    titleLarge = DefaultWeModernTypography.titleLarge.roundedGoogleSansFlex(),
    titleMedium = DefaultWeModernTypography.titleMedium.roundedGoogleSansFlex(),
    titleSmall = DefaultWeModernTypography.titleSmall.roundedGoogleSansFlex(),
    bodyLarge = DefaultWeModernTypography.bodyLarge.roundedGoogleSansFlex(),
    bodyMedium = DefaultWeModernTypography.bodyMedium.roundedGoogleSansFlex(),
    bodySmall = DefaultWeModernTypography.bodySmall.roundedGoogleSansFlex(),
    labelLarge = DefaultWeModernTypography.labelLarge.roundedGoogleSansFlex(),
    labelMedium = DefaultWeModernTypography.labelMedium.roundedGoogleSansFlex(),
    labelSmall = DefaultWeModernTypography.labelSmall.roundedGoogleSansFlex(),
)

private fun TextStyle.roundedGoogleSansFlex() = copy(fontFamily = GoogleSansFlexRounded)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006C48),
    onPrimary = Color(0xFFF2FFF7),
    primaryContainer = Color(0xFF8DF8BF),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4D6356),
    onSecondary = Color(0xFFF4FFF7),
    secondaryContainer = Color(0xFFCFE9D8),
    onSecondaryContainer = Color(0xFF0A1F15),
    tertiary = Color(0xFF3C6472),
    onTertiary = Color(0xFFF2FBFF),
    tertiaryContainer = Color(0xFFC0E9F9),
    onTertiaryContainer = Color(0xFF001F28),
    surface = Color(0xFFF7FAF5),
    onSurface = Color(0xFF191C1A),
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
    surface = Color(0xFF101512),
    onSurface = Color(0xFFE1E4DF),
)
