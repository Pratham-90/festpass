package com.festpass.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.festpass.auth.AppUserPrincipal;
import com.festpass.user.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/**
 * The headline guarantees under real contention: many HTTP requests released at the same instant
 * (CountDownLatch) against a real PostgreSQL, with exact counts asserted.
 */
class ConcurrencyIntegrationTest extends BookingTestSupport {

    @BeforeEach
    void warmUp() throws Exception {
        // The first csrf() call in a context swaps in a test token repository; do it before any race.
        call(get("/api/health"), student(), null);
    }

    @Test
    void twoHundredUsersRaceForOneSeatAndExactlyOneWins() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = publishedSeatedEvent(organizer, "Seat Race");
        long seatId = seatIds(eventId).get(0);
        List<AppUserPrincipal> racers = students(200);

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (AppUserPrincipal user : racers) {
            tasks.add(() -> holdSeats(eventId, user, List.of(seatId)).andReturn().getResponse().getStatus());
        }
        Map<Integer, Long> byStatus = count(runTogether(tasks));

        assertThat(byStatus).containsOnlyKeys(201, 409);
        assertThat(byStatus.get(201)).isEqualTo(1);
        assertThat(byStatus.get(409)).isEqualTo(199);
        assertThat(seatStatus(seatId)).isEqualTo("HELD");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM holds WHERE event_id = ? AND status = 'ACTIVE'", Long.class, eventId)).isEqualTo(1);
    }

    @Test
    void threeHundredRequestsForACapacityOfOneHundredSellExactlyOneHundred() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long[] event = publishedGaEvent(organizer, "GA Rush", 100);
        List<AppUserPrincipal> racers = students(300);

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (AppUserPrincipal user : racers) {
            tasks.add(() -> holdGa(event[0], user, event[1], 1).andReturn().getResponse().getStatus());
        }
        Map<Integer, Long> byStatus = count(runTogether(tasks));

        assertThat(byStatus.get(201)).isEqualTo(100);
        assertThat(byStatus.get(409)).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT reserved FROM ticket_tiers WHERE id = ?", Integer.class, event[1]))
                .isEqualTo(100);
    }

    @Test
    void oneUsersParallelRequestsCannotBeatTheTicketLimit() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = publishedSeatedEvent(organizer, "Greedy User"); // limit: 4 tickets per user
        List<Long> seats = seatIds(eventId);
        AppUserPrincipal greedy = student();

        // 5 requests (the per-second rate limit) for 5 different seats, all at once.
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            long seat = seats.get(i);
            tasks.add(() -> holdSeats(eventId, greedy, List.of(seat)).andReturn().getResponse().getStatus());
        }
        Map<Integer, Long> byStatus = count(runTogether(tasks));

        assertThat(byStatus.get(201)).isEqualTo(4);
        assertThat(byStatus.get(409)).isEqualTo(1);
    }

    @Test
    void parallelPaymentsWithDifferentKeysCreateOneSetOfTickets() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = publishedSeatedEvent(organizer, "Double Pay");
        AppUserPrincipal buyer = student();
        List<Long> seats = seatIds(eventId).subList(0, 2);
        String holdId = read(holdSeats(eventId, buyer, seats)).get("holdId").asText();
        long orderId = read(createOrder(holdId, buyer, newKey())).get("id").asLong();

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> pay(orderId, buyer, "SUCCESS", newKey()).andReturn().getResponse().getStatus());
        }
        assertThat(runTogether(tasks)).containsOnly(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tickets WHERE order_id = ?", Long.class, orderId))
                .isEqualTo(2);
    }

    private static Map<Integer, Long> count(List<Integer> statuses) {
        return statuses.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }
}
