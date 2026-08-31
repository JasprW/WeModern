package me.jaspr.wemodern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ServiceAccountConversationTest {
    @Test
    public void recognizesSupportedContainerTitles() {
        assertTrue(ServiceAccountConversation.isContainerTitle("Service Accounts"));
        assertTrue(ServiceAccountConversation.isContainerTitle(" service accounts "));
        assertTrue(ServiceAccountConversation.isContainerTitle("服务号"));
        assertTrue(ServiceAccountConversation.isContainerTitle("服務號"));
        assertFalse(ServiceAccountConversation.isContainerTitle("Service Account Notice"));
    }

    @Test
    public void legacyLocalizedIdsMapToOneConversation() {
        assertTrue(ServiceAccountConversation.isLegacyConversation(
                "wechat:Service Accounts",
                "Service Accounts"
        ));
        assertTrue(ServiceAccountConversation.isLegacyConversation(
                "wechat:服务号",
                "服务号"
        ));
        assertFalse(ServiceAccountConversation.isLegacyConversation(
                "wechat:Alice",
                "Alice"
        ));
    }

    @Test
    public void participantIdentityUsesStableSourceNotificationId() {
        assertEquals(
                "wechat-service:-1713208922",
                ServiceAccountConversation.participantKey(-1713208922)
        );
    }

    @Test
    public void legacyFallbackUsesOnlyExactParticipantConversation() {
        assertEquals(
                "wechat:跃捷国际物流集运转运",
                ServiceAccountConversation.legacySenderConversationId(
                        "跃捷国际物流集运转运"
                )
        );
        assertNull(ServiceAccountConversation.legacySenderConversationId("Service Accounts"));
        assertNull(ServiceAccountConversation.legacySenderConversationId("服务号"));
        assertNull(ServiceAccountConversation.legacySenderConversationId("  "));
    }

    @Test
    public void parserBuildsOneGroupConversationWithDistinctParticipants() {
        WeChatNotificationService.ParsedNotification english =
                WeChatNotificationService.WeChatParser.parseMessage(
                        "Service Accounts",
                        "Shipping Service: Package received",
                        null,
                        101
                );
        WeChatNotificationService.ParsedNotification chinese =
                WeChatNotificationService.WeChatParser.parseMessage(
                        "服务号",
                        "支付服务: 还款提醒",
                        null,
                        202
                );

        assertEquals(ServiceAccountConversation.CONVERSATION_ID, english.conversationKey);
        assertEquals(ServiceAccountConversation.CONVERSATION_ID, chinese.conversationKey);
        assertEquals("Shipping Service", english.sender);
        assertEquals("支付服务", chinese.sender);
        assertEquals("wechat-service:101", english.senderKey);
        assertEquals("wechat-service:202", chinese.senderKey);
        assertTrue(english.groupConversation);
        assertTrue(english.serviceAccountConversation);
        assertTrue(chinese.groupConversation);
        assertTrue(chinese.serviceAccountConversation);
    }
}
