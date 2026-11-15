package edu.bits.itsm.repository;

import edu.bits.itsm.domain.notification.Notification;
import edu.bits.itsm.domain.notification.NotificationChannelType;
import edu.bits.itsm.domain.notification.TextSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop50ByRecipientIdOrderBySentAtDesc(Long recipientId);
    long countByRecipientIdAndChannelAndReadFalse(Long recipientId, NotificationChannelType channel);
    List<Notification> findTop100ByOrderBySentAtDesc();

    // The two writes below can happen at the same moment (Claude's rewrite lands while the reporter opens the bell).
    // Each one changes only its own columns, so neither can put back the other's old value, as saving a whole
    // loaded row would.

    /** Replaces only the plain-language line and its source, so a "mark read" committed meanwhile stays. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.plainText = :text, n.textSource = :source where n.id in :ids")
    int describePlainly(@Param("ids") Collection<Long> ids, @Param("text") String text, @Param("source") TextSource source);

    /** Sets only the read flag, and only on this recipient's own rows, so a rewrite committed meanwhile stays. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.read = true where n.recipientId = :recipientId and n.id in :ids")
    int markRead(@Param("recipientId") Long recipientId, @Param("ids") Collection<Long> ids);
}
