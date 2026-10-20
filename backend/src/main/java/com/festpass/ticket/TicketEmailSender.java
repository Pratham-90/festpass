package com.festpass.ticket;

import com.festpass.ticket.TicketService.OrderDocuments;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

/**
 * Sends the confirmation email with the PDF tickets and an .ics file.
 * Runs after the payment transaction commits, on a background thread, so a slow or failed
 * email never delays or undoes a payment (PLAN.md 5.4).
 */
@Component
public class TicketEmailSender {

    private static final Logger log = LoggerFactory.getLogger(TicketEmailSender.class);

    private final TicketService ticketService;
    private final JavaMailSender mailSender;
    private final String from;
    private final DateTimeFormatter dateFormat;

    public TicketEmailSender(TicketService ticketService, JavaMailSender mailSender,
                             @Value("${festpass.mail-from}") String from,
                             @Value("${festpass.display-zone}") ZoneId zone) {
        this.ticketService = ticketService;
        this.mailSender = mailSender;
        this.from = from;
        this.dateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM, h:mm a", Locale.ENGLISH).withZone(zone);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent paid) {
        try {
            send(ticketService.documentsForOrder(paid.orderId()), paid.orderId());
        } catch (Exception e) {
            log.warn("Could not send the ticket email for order {}", paid.orderId(), e);
        }
    }

    private void send(OrderDocuments docs, Long orderId) throws MessagingException {
        if (docs.event().isSandbox()) {
            return; // Stress Arena bots don't get email.
        }
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper mail = new MimeMessageHelper(message, true, "UTF-8");
        mail.setFrom(from);
        mail.setTo(docs.buyer().getEmail());
        mail.setSubject("Your tickets: " + docs.event().getTitle());
        mail.setText(plainText(docs), html(docs));
        mail.addAttachment("festpass-tickets-" + orderId + ".pdf", new ByteArrayResource(docs.pdf()), "application/pdf");
        mail.addAttachment(docs.event().getSlug() + ".ics",
                new ByteArrayResource(docs.ics().getBytes(StandardCharsets.UTF_8)), "text/calendar");
        mailSender.send(message);
    }

    private String plainText(OrderDocuments docs) {
        StringBuilder text = new StringBuilder()
                .append("Hi ").append(docs.buyer().getName()).append(",\n\n")
                .append("You're going to ").append(docs.event().getTitle()).append("!\n")
                .append(dateFormat.format(docs.event().getStartsAt())).append(" at ").append(docs.event().getVenueName())
                .append("\n\n");
        docs.tickets().forEach(t -> text.append("- ").append(t.tier().getName()).append(", ").append(t.seatLabel()).append('\n'));
        return text.append("\nYour tickets are attached as a PDF. Show the QR code at the gate.\n\nFestPass").toString();
    }

    private String html(OrderDocuments docs) {
        StringBuilder rows = new StringBuilder();
        docs.tickets().forEach(t -> rows.append("<li>").append(esc(t.tier().getName())).append(", ")
                .append(esc(t.seatLabel())).append("</li>"));
        return """
                <div style="font-family:Arial,sans-serif;color:#14141f">
                  <h2 style="margin:0 0 8px">You're going to %s!</h2>
                  <p style="margin:0">%s at %s</p>
                  <ul>%s</ul>
                  <p>Your tickets are attached as a PDF. Show the QR code at the gate.</p>
                  <p style="color:#6b6b80">FestPass</p>
                </div>
                """.formatted(esc(docs.event().getTitle()), esc(dateFormat.format(docs.event().getStartsAt())),
                esc(docs.event().getVenueName()), rows);
    }

    private static String esc(String text) {
        return HtmlUtils.htmlEscape(text);
    }
}
