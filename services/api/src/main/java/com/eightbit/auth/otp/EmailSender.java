package com.eightbit.auth.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends mail via SMTP when {@code spring.mail.host} is configured (e.g. a free Brevo/Resend relay);
 * otherwise logs the message so OTP works in dev with no email server.
 */
@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final ObjectProvider<JavaMailSender> mailProvider;
    private final String mailHost;
    private final String from;

    public EmailSender(ObjectProvider<JavaMailSender> mailProvider,
                       @Value("${spring.mail.host:}") String mailHost,
                       @Value("${app.otp.from-address:8bit@iiitb.ac.in}") String from) {
        this.mailProvider = mailProvider;
        this.mailHost = mailHost;
        this.from = from;
    }

    public void send(String to, String subject, String body) {
        JavaMailSender sender = mailProvider.getIfAvailable();
        if (mailHost == null || mailHost.isBlank() || sender == null) {
            log.info("[DEV EMAIL — no SMTP configured]\n  to: {}\n  subject: {}\n  body: {}", to, subject, body);
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
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
