package edu.bits.itsm.notification;

/** Port for an external email provider (what an Adapter wraps). */
public interface EmailGateway {
    void sendEmail(String to, String subject, String body);
}
