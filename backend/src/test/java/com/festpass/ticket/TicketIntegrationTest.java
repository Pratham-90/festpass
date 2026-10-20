package com.festpass.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.festpass.auth.AppUserPrincipal;
import com.festpass.booking.BookingTestSupport;
import com.festpass.user.Role;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockHttpSession;

class TicketIntegrationTest extends BookingTestSupport {

    @Value("${test.mailpit.api}")
    String mailpitApi;

    @Test
    void walletShowsOnlyMyTicketsWithWorkingDownloads() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = publishedSeatedEvent(organizer, "Wallet Show");
        AppUserPrincipal buyer = student();
        buySeats(eventId, buyer, seatIds(eventId).subList(0, 2));
        buySeats(eventId, student(), List.of(seatIds(eventId).get(2)));

        JsonNode wallet = read(call(get("/api/me/tickets"), buyer, null).andExpect(status().isOk()));
        assertThat(wallet).hasSize(2);
        JsonNode ticket = wallet.get(0);
        assertThat(ticket.get("eventTitle").asText()).isEqualTo("Wallet Show");
        assertThat(ticket.get("tierName").asText()).isEqualTo("Standard");
        assertThat(ticket.get("row").asText()).isEqualTo("A");
        String id = ticket.get("id").asText();

        // The QR image decodes back to the ticket's signed code.
        byte[] png = call(get("/api/tickets/" + id + "/qr.png"), buyer, null)
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andReturn().getResponse().getContentAsByteArray();
        var image = ImageIO.read(new ByteArrayInputStream(png));
        String decoded = new QRCodeReader().decode(
                new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)))).getText();
        assertThat(decoded).isEqualTo(ticket.get("code").asText());

        byte[] pdf = call(get("/api/tickets/" + id + "/pdf"), buyer, null)
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        String ics = call(get("/api/tickets/" + id + "/calendar.ics"), buyer, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").contains("SUMMARY:Wallet Show").contains("END:VEVENT");

        // Another student can't see or download it.
        AppUserPrincipal stranger = student();
        call(get("/api/tickets/" + id), stranger, null).andExpect(status().isNotFound());
        call(get("/api/tickets/" + id + "/pdf"), stranger, null).andExpect(status().isNotFound());
        call(get("/api/me/tickets"), stranger, null).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void paymentSendsAConfirmationEmailWithPdfAndCalendar() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = publishedSeatedEvent(organizer, "Email Night");
        AppUserPrincipal buyer = student();
        buySeats(eventId, buyer, seatIds(eventId).subList(0, 2));

        JsonNode message = waitForEmailTo(buyer.email());
        assertThat(message.get("Subject").asText()).isEqualTo("Your tickets: Email Night");

        JsonNode full = mailpit("/api/v1/message/" + message.get("ID").asText());
        List<String> attachments = full.get("Attachments").findValuesAsText("ContentType");
        assertThat(attachments).anyMatch(t -> t.startsWith("application/pdf"));
        assertThat(attachments).anyMatch(t -> t.startsWith("text/calendar"));
        assertThat(full.get("Text").asText()).contains("Hall · Row A · Seat 1").contains("Hall · Row A · Seat 2");
    }

    /** The email is sent in the background after the payment commits, so poll Mailpit for a few seconds. */
    private JsonNode waitForEmailTo(String email) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        String query = URLEncoder.encode("to:\"" + email + "\"", StandardCharsets.UTF_8);
        while (Instant.now().isBefore(deadline)) {
            JsonNode found = mailpit("/api/v1/search?query=" + query);
            if (found.get("messages_count").asInt() > 0) {
                return found.get("messages").get(0);
            }
            Thread.sleep(200);
        }
        throw new AssertionError("No email arrived for " + email);
    }

    private JsonNode mailpit(String path) throws Exception {
        try (HttpClient http = HttpClient.newHttpClient()) {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(mailpitApi + path)).build(),
                    HttpResponse.BodyHandlers.ofString());
            return objectMapper.readTree(response.body());
        }
    }
}
