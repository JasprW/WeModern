package me.jaspr.wemodern

import android.content.Intent
import android.content.LocusId
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

class BubbleConversationActivity : ComponentActivity() {
    private var conversationState by mutableStateOf<ConversationBubbleState?>(null)
    private var trampolineEnabled by mutableStateOf(false)
    private var observedConversationId: String? = null
    private var observing = false

    private val stateListener = ConversationBubbleStore.Listener { updated ->
        runOnUiThread {
            if (updated == null) {
                finishAndRemoveTask()
            } else {
                conversationState = updated
                title = updated.title
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            WeModernTheme {
                BubbleConversationScreen(
                    state = conversationState,
                    opensWeChatHome = conversationState?.let {
                        BubbleTrampolineBehavior.shouldOpenWeChatHome(
                            it.conversationId,
                            trampolineEnabled,
                        )
                    } == true,
                    onOpenConversation = ::openConversation,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        observing = true
        observedConversationId?.let { ConversationBubbleStore.addListener(it, stateListener) }
    }

    override fun onStop() {
        observedConversationId?.let { ConversationBubbleStore.removeListener(it, stateListener) }
        observing = false
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val restored = ConversationBubbleState.from(intent) ?: return
        if (observing && observedConversationId != restored.conversationId) {
            observedConversationId?.let { ConversationBubbleStore.removeListener(it, stateListener) }
        }
        observedConversationId = restored.conversationId
        val current = ConversationBubbleStore.get(restored.conversationId)
        val resolved = current ?: restored.also(ConversationBubbleStore::update)
        conversationState = resolved
        trampolineEnabled = BubbleTrampolineBehavior.isEnabled(this)
        title = resolved.title
        if (Build.VERSION.SDK_INT >= 30) {
            setLocusContext(LocusId(resolved.conversationId), null)
        }
        if (observing) ConversationBubbleStore.addListener(restored.conversationId, stateListener)
    }

    private fun openConversation() {
        val state = conversationState ?: return
        if (BubbleTrampolineBehavior.shouldOpenWeChatHome(
                state.conversationId,
                trampolineEnabled,
            ) && WeChatLauncher.openInCurrentTask(this)
        ) {
            return
        }
        if (!ConversationShortcuts.openConversation(this, state.conversationId, state.contentIntent)) {
            WeChatLauncher.open(this)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BubbleConversationScreen(
    state: ConversationBubbleState?,
    opensWeChatHome: Boolean,
    onOpenConversation: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state?.title?.ifBlank { stringResource(R.string.app_name) }
                            ?: stringResource(R.string.app_name),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    Surface(
                        modifier = Modifier
                            .padding(start = 12.dp, end = 4.dp)
                            .size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.ChatBubble,
                                contentDescription = null,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
            ) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp),
                    onClick = onOpenConversation,
                    enabled = state != null,
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(
                            if (opensWeChatHome) {
                                R.string.bubble_open_wechat_home
                            } else {
                                R.string.bubble_open_conversation
                            }
                        )
                    )
                }
            }
        },
    ) { padding ->
        if (state == null || state.messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.bubble_no_messages),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val listState = rememberLazyListState()
            LaunchedEffect(state.messages.size, state.messages.last().timestamp) {
                listState.animateScrollToItem(state.messages.lastIndex)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = state.messages,
                    key = { "${it.timestamp}:${it.sender}:${it.text}" },
                ) { message ->
                    BubbleMessageCard(message)
                }
            }
        }
    }
}

@Composable
private fun BubbleMessageCard(message: ConversationBubbleState.Message) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = message.sender,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = DateFormat.getTimeFormat(androidx.compose.ui.platform.LocalContext.current)
                        .format(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
