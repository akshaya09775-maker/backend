package app.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends plain-text emails. When no SMTP server is configured (spring.mail.host is empty) nothing is
 * sent: the email is logged instead, so local development works without a mail server.
 */
@Slf4j
@Service
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;
    private final String baseUrl;
    private final String appName;
    private final boolean logBodyWhenNotSent;
    private final boolean smtpConfigured;

    public EmailService(ObjectProvider<JavaMailSender> mailSender,
                        @Value("${app.mail.from:no-reply@localhost}") String from,
                        @Value("${app.base-url:http://localhost:5173}") String baseUrl,
                        @Value("${app.name:JobPortal}") String appName,
                        @Value("${app.mail.log-body-when-not-sent:false}") boolean logBodyWhenNotSent,
                        @Value("${spring.mail.host:}") String smtpHost) {
        this.smtpConfigured = smtpHost != null && !smtpHost.isBlank();
        this.mailSender = mailSender;
        this.from = from;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.appName = appName;
        this.logBodyWhenNotSent = logBodyWhenNotSent;
    }

    public String url(String path) {
        if (path == null || path.isBlank()) return baseUrl;
        return baseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    private void send(String to, String subject, String body) {
        if (to == null || to.isBlank() || to.endsWith("@deleted.invalid")) return;
        JavaMailSender sender = smtpConfigured ? mailSender.getIfAvailable() : null;
        if (sender == null) {
            if (logBodyWhenNotSent) {
                log.info("[email not sent - SMTP not configured] to={} subject={}\n{}", to, subject, body);
            } else {
                log.info("[email not sent - SMTP not configured] to={} subject={}", to, subject);
            }
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            sender.send(msg);
        } catch (Exception e) {
            log.warn("Could not send email to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendWelcome(String to, String name) {
        send(to, "Welcome to " + appName,
                "Hi " + name + ",\n\nYour " + appName + " account is ready.\n\nSign in: " + url("/login")
                        + "\n\nIf you did not create this account, please ignore this email.\n\n- The " + appName + " team");
    }

    @Async
    public void sendPasswordReset(String to, String name, String token) {
        send(to, "Reset your " + appName + " password",
                "Hi " + name + ",\n\nWe received a request to reset your password. This link works once and expires in 1 hour:\n\n"
                        + url("/reset-password?token=" + token)
                        + "\n\nIf you did not ask for this, you can ignore this email; your password will not change.\n\n- The "
                        + appName + " team");
    }

    @Async
    public void sendPasswordChanged(String to, String name) {
        send(to, "Your " + appName + " password was changed",
                "Hi " + name + ",\n\nYour password was just changed. If this was not you, reset it immediately: "
                        + url("/forgot-password") + "\n\n- The " + appName + " team");
    }

    @Async
    public void sendNotification(String to, String name, String message, String link) {
        send(to, appName + ": " + (message.length() > 70 ? message.substring(0, 67) + "..." : message),
                "Hi " + name + ",\n\n" + message + "\n\nOpen " + appName + ": " + url(link)
                        + "\n\nYou can turn these emails off in Account settings: " + url("/account")
                        + "\n\n- The " + appName + " team");
    }

    @Async
    public void sendAccountDeleted(String to, String name) {
        send(to, "Your " + appName + " account was deleted",
                "Hi " + name + ",\n\nYour account and personal data have been deleted as requested.\n\n- The "
                        + appName + " team");
    }
}
