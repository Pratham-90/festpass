package com.festpass.booking;

import com.festpass.event.AvailabilityChanged;
import com.festpass.event.TicketTierRepository;
import com.festpass.seating.SeatStatus;
import com.festpass.seating.SeatStatusWriter;
import com.festpass.seating.SeatsChanged;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ends an active hold and gives its seats or GA tickets back. Shared by the expiry job,
 * "cancel hold" and failed payments.
 */
@Component
public class HoldReleaser {

    private final HoldRepository holds;
    private final SeatStatusWriter seatWriter;
    private final TicketTierRepository tiers;
    private final ApplicationEventPublisher events;

    public HoldReleaser(HoldRepository holds, SeatStatusWriter seatWriter, TicketTierRepository tiers,
                        ApplicationEventPublisher events) {
        this.holds = holds;
        this.seatWriter = seatWriter;
        this.tiers = tiers;
        this.events = events;
    }

    /**
     * @return true if this call ended the hold. Seats are freed (or GA tickets un-reserved) only then,
     *         so a hold is never given back twice even when several callers race.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean end(Hold hold, Hold.Status endStatus) {
        if (endStatus != Hold.Status.EXPIRED && endStatus != Hold.Status.RELEASED) {
            throw new IllegalArgumentException("A hold can only be ended as EXPIRED or RELEASED");
        }
        if (holds.end(hold.getId(), endStatus.name()) != 1) {
            return false;
        }
        if (hold.isGeneralAdmission()) {
            tiers.unreserve(hold.getTierId(), hold.getQuantity());
            events.publishEvent(new AvailabilityChanged(hold.getEventId()));
        } else {
            List<Long> freed = seatWriter.free(hold.getId());
            if (!freed.isEmpty()) {
                events.publishEvent(new SeatsChanged(hold.getEventId(), freed, SeatStatus.AVAILABLE));
            }
        }
        events.publishEvent(new HoldEnded(hold.getId(), hold.getUserId(), hold.getEventId(), endStatus));
        return true;
    }
}
