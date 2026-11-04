package edu.bits.itsm.domain.notification;

/** Delivery mechanisms. Adding a new one = new enum constant + new NotificationChannel bean. */
public enum NotificationChannelType {
    EMAIL, SMS, IN_APP
}
