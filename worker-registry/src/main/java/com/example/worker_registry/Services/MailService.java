package com.example.worker_registry.Services;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

@Service
public class MailService {

  private static final Logger log = LoggerFactory.getLogger(MailService.class);

  private final JavaMailSender mailSender;

  @Value("${spring.mail.username}")
  private String from; // Debe ser la misma cuenta usada en spring.mail.username
  
  @Value("${app.mail.enabled:true}")
  private boolean mailEnabled;

  public MailService(JavaMailSender mailSender) {
    this.mailSender = mailSender;
  }

  @Async
  public void send(String to, String subject, String body) {
    try {
      if (!mailEnabled) {
        log.info("[MAIL-DISABLED] Simulando envio a {} | Asunto: {}", to, subject);
        return;
      }
      MimeMessage mime = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());

      // From debe coincidir con el usuario (o un alias verificado en Gmail)
      helper.setFrom(new InternetAddress(from, "Conecta2 No-Reply"));
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(body, false); // true si envías HTML

      mailSender.send(mime);
      log.info("Correo enviado a {}", to);
    } catch (Exception e) {
      log.error("Error enviando correo a {}: {}", to, e.getMessage(), e);
      // Si quieres que un fallo de correo NO tumbe el registro, no relances la excepción.
      // Si prefieres que falle el registro, relanza RuntimeException.
    }
  }
}
