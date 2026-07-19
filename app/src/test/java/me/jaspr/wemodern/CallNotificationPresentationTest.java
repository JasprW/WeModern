package me.jaspr.wemodern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ServiceInfo;

import org.junit.Test;

public class CallNotificationPresentationTest {
    @Test
    public void callStyleStartsAtAndroid12() {
        assertFalse(CallNotificationPresentation.supportsCallStyle(30));
        assertTrue(CallNotificationPresentation.supportsCallStyle(31));
        assertTrue(CallNotificationPresentation.supportsCallStyle(37));
    }

    @Test
    public void api37CallStyleUsesSpecialUseForegroundLifecycle() {
        assertFalse(CallNotificationPresentation.requiresForegroundCallStyle(36));
        assertTrue(CallNotificationPresentation.requiresForegroundCallStyle(37));
        assertEquals(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                CallNotificationPresentation.foregroundServiceType()
        );
    }
}
