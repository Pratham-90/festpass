package com.festpass.ticket;

import java.time.Instant;
import java.util.UUID;

public final class TicketDtos {

    private TicketDtos() {}

    /** A ticket in the wallet. {@code code} is the text in the QR code; seat fields are null for GA. */
    public record TicketView(
            UUID id, Long orderId, Long eventId, String eventSlug, String eventTitle, String venueName,
            Instant startsAt, Instant endsAt, Long tierId, String tierName, String tierColor, long pricePaise,
            String section, String row, Integer seatNumber, String seatLabel,
            String holderName, String code, Instant checkedInAt, Instant issuedAt) {

        static TicketView of(TicketDetails d) {
            var t = d.ticket();
            var e = d.event();
            var seat = d.seat();
            return new TicketView(t.getId(), t.getOrderId(), e.getId(), e.getSlug(), e.getTitle(), e.getVenueName(),
                    e.getStartsAt(), e.getEndsAt(), d.tier().getId(), d.tier().getName(), d.tier().getColorHex(),
                    d.tier().getPricePaise(),
                    seat == null ? null : seat.getSection(), seat == null ? null : seat.getRowLabel(),
                    seat == null ? null : seat.getSeatNumber(), d.seatLabel(),
                    t.getHolderName(), d.code(), t.getCheckedInAt(), t.getCreatedAt());
        }
    }
}
