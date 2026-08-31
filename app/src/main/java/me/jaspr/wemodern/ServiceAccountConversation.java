package me.jaspr.wemodern;

import java.util.Locale;

final class ServiceAccountConversation {
    static final String CONVERSATION_ID = "wechat:service_accounts";

    private static final String ENGLISH_TITLE = "service accounts";
    private static final String SIMPLIFIED_CHINESE_TITLE = "服务号";
    private static final String TRADITIONAL_CHINESE_TITLE = "服務號";

    private ServiceAccountConversation() {
    }

    static boolean isContainerTitle(CharSequence title) {
        String normalized = title == null
                ? ""
                : title.toString().trim().toLowerCase(Locale.ROOT);
        return ENGLISH_TITLE.equals(normalized)
                || SIMPLIFIED_CHINESE_TITLE.equals(normalized)
                || TRADITIONAL_CHINESE_TITLE.equals(normalized);
    }

    static boolean isConversationId(String conversationId) {
        return CONVERSATION_ID.equals(conversationId);
    }

    static boolean isLegacyConversation(String conversationId, CharSequence title) {
        if (isConversationId(conversationId)) return true;
        if (isContainerTitle(title)) return true;
        if (conversationId == null || !conversationId.startsWith("wechat:")) return false;
        return isContainerTitle(conversationId.substring("wechat:".length()));
    }

    static String participantKey(int notificationId) {
        return "wechat-service:" + notificationId;
    }

    static String legacySenderConversationId(CharSequence sender) {
        String normalized = sender == null ? "" : sender.toString().trim();
        if (normalized.isEmpty() || isContainerTitle(normalized)) return null;
        return "wechat:" + normalized;
    }

    static String[] legacyConversationIds() {
        return new String[] {
                "wechat:Service Accounts",
                "wechat:服务号",
                "wechat:服務號"
        };
    }
}
