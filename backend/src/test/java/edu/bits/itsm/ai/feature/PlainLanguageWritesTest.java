package edu.bits.itsm.ai.feature;

import edu.bits.itsm.ai.AiBackgroundTasks;
import edu.bits.itsm.ai.AiFeature;
import edu.bits.itsm.ai.AiTestSupport;
import edu.bits.itsm.ai.FakeAiGateway;
import edu.bits.itsm.domain.notification.Notification;
import edu.bits.itsm.domain.notification.NotificationChannelType;
import edu.bits.itsm.domain.notification.TextSource;
import edu.bits.itsm.domain.user.User;
import edu.bits.itsm.event.IncidentEventType;
import edu.bits.itsm.notification.InAppNotificationChannel;
import edu.bits.itsm.repository.NotificationRepository;
import edu.bits.itsm.security.CurrentUser;
import edu.bits.itsm.web.CatalogController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TC-88: Claude's background rewrite and the bell's "mark read" may land at the same moment. Each is one targeted
 * update of its own columns; neither loads a whole row and saves it back, which is what let one undo the other.
 */
class PlainLanguageWritesTest {

    private final FakeAiGateway gateway = new FakeAiGateway();
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final InAppNotificationChannel inApp = mock(InAppNotificationChannel.class);

    @Test @DisplayName("TC-88 Targeted writes only: the rewrite sets just the plain line and its source, mark-read just the read flag; no row is saved back whole")
    void targetedWrites() {
        PlainLanguageUpdateService updates = new PlainLanguageUpdateService(AiTestSupport.liveMode(gateway), notifications, inApp,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), mock(AiBackgroundTasks.class));
        Notification row = new Notification(9L, "emp.raj", NotificationChannelType.IN_APP, "INC-000042 resolved", "Resolved.", 42L, "RESOLVED");
        when(notifications.describePlainly(List.of(5L), "Your VPN problem is fixed.", TextSource.AI)).thenReturn(1);
        when(notifications.findAllById(List.of(5L))).thenReturn(List.of(row));
        gateway.answer(AiFeature.STATUS_MESSAGE, "{\"message\":\"  Your VPN problem is fixed. \"}");

        updates.rewrite(new StatusFacts(IncidentEventType.RESOLVED, "INC-000042", "VPN drops", "Priya Nair", "urgent", "New client"), List.of(5L));
        InOrder order = inOrder(notifications, inApp);
        order.verify(notifications).describePlainly(List.of(5L), "Your VPN problem is fixed.", TextSource.AI);
        order.verify(notifications).findAllById(List.of(5L));     // re-read after the update, only to push it
        order.verify(inApp).deliver(row);

        User me = mock(User.class);
        when(me.getId()).thenReturn(9L);
        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.get()).thenReturn(me);
        Notification older = mock(Notification.class);
        when(older.getId()).thenReturn(7L);
        when(notifications.findTop50ByRecipientIdOrderBySentAtDesc(9L)).thenReturn(List.of(older));
        CatalogController bell = new CatalogController(null, null, null, null, null, null, notifications, currentUser);
        bell.markRead(5L);
        bell.markAllRead();
        verify(notifications).markRead(9L, List.of(5L));
        verify(notifications).markRead(9L, List.of(7L));

        verify(notifications, never()).save(any());
        verify(notifications, never()).saveAll(any());
        verify(notifications, never()).saveAndFlush(any());
    }
}
