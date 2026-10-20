package com.festpass.booking;

import com.festpass.event.TicketTierRepository;
import com.festpass.seating.SeatRepository;
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
    private final SeatRepository seats;
    private final TicketTierRepository tiers;

    public HoldReleaser(HoldRepository holds, SeatRepository seats, TicketTierRepository tiers) {
        this.holds = holds;
        this.seats = seats;
        this.tiers = tiers;
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
        } else {
            seats.free(hold.getId());
        }
        return true;
    }
}
