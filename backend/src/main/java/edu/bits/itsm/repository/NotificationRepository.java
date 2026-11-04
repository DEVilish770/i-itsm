package edu.bits.itsm.repository;

import edu.bits.itsm.domain.notification.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop50ByRecipientIdOrderBySentAtDesc(Long recipientId);
    long countByRecipientIdAndReadFalse(Long recipientId);
    List<Notification> findTop100ByOrderBySentAtDesc();
}
