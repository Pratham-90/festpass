package com.festpass.ticket;

import com.festpass.auth.AppUserPrincipal;
import com.festpass.ticket.TicketDtos.TicketView;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TicketController {

    private static final MediaType CALENDAR = new MediaType("text", "calendar", StandardCharsets.UTF_8);

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/api/me/tickets")
    public List<TicketView> myTickets(@AuthenticationPrincipal AppUserPrincipal me) {
        return ticketService.myTickets(me);
    }

    @GetMapping("/api/tickets/{id}")
    public TicketView get(@PathVariable UUID id, @AuthenticationPrincipal AppUserPrincipal me) {
        return ticketService.get(id, me);
    }

    @GetMapping("/api/tickets/{id}/qr.png")
    public ResponseEntity<byte[]> qr(@PathVariable UUID id, @AuthenticationPrincipal AppUserPrincipal me) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(ticketService.qrPng(id, me));
    }

    @GetMapping("/api/tickets/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id, @AuthenticationPrincipal AppUserPrincipal me) {
        return download(ticketService.pdf(id, me), MediaType.APPLICATION_PDF, "festpass-ticket-" + id + ".pdf");
    }

    @GetMapping("/api/tickets/{id}/calendar.ics")
    public ResponseEntity<byte[]> calendar(@PathVariable UUID id, @AuthenticationPrincipal AppUserPrincipal me) {
        return download(ticketService.calendar(id, me).getBytes(StandardCharsets.UTF_8), CALENDAR, "festpass-event.ics");
    }

    private static ResponseEntity<byte[]> download(byte[] body, MediaType type, String filename) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
