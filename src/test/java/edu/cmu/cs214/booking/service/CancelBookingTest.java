package edu.cmu.cs214.booking.service;

import edu.cmu.cs214.booking.domain.*;
import edu.cmu.cs214.booking.repo.InMemoryBookingStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CancelBookingTest {

    private InMemoryBookingStore store;
    private BookingService svc;
    private final Room roomA = new Room("A", "Room A", 4);
    private final Room roomB = new Room("B", "Room B", 4);
    private final User alice = new User("alice", "Alice");
    private final User bob   = new User("bob",   "Bob");
    private final User carol = new User("carol", "Carol");
    private final User dave  = new User("dave",  "Dave");

    private void newService() {
        store = new InMemoryBookingStore();
        svc = new BookingService(store);
    }

    // S1: 基本取消，没有候补者
    @Test
    void cancelRemovesBookingWithNoWaitlist() {
        newService();
        BookingResult r = svc.book(roomA, alice, new TimeInterval(600, 660));
        Booking a = assertInstanceOf(BookingResult.Confirmed.class, r).booking();

        svc.cancelBooking(a.id());

        assertTrue(svc.listBookings(roomA).isEmpty());
        assertTrue(store.waitlistForRoom(roomA).isEmpty());
    }

    // S2: 无效 id，什么都不做
    @Test
    void cancelUnknownIdDoesNothing() {
        newService();
        BookingResult r = svc.book(roomA, alice, new TimeInterval(600, 660));
        assertInstanceOf(BookingResult.Confirmed.class, r);

        svc.cancelBooking("does-not-exist");

        assertEquals(1, svc.listBookings(roomA).size());
    }

    // S3: 晋升（重点）
    @Test
    void cancellingPromotesTheWaitlistedUser() {
        newService();
        BookingResult first = svc.book(roomA, alice, new TimeInterval(600, 660));
        Booking a = assertInstanceOf(BookingResult.Confirmed.class, first).booking();
        assertInstanceOf(BookingResult.Waitlisted.class,
                svc.book(roomA, bob, new TimeInterval(630, 700)));

        svc.cancelBooking(a.id());

        List<Booking> bookings = svc.listBookings(roomA);
        assertEquals(1, bookings.size());
        assertEquals("bob", bookings.get(0).user().id());
        assertEquals(new TimeInterval(630, 700), bookings.get(0).interval());
        assertTrue(store.waitlistForRoom(roomA).isEmpty());
    }

    // S4: 先到先得 + 至多一人
    @Test
    void promotesOnlyEarliestFittingWaiter() {
        newService();
        BookingResult first = svc.book(roomA, alice, new TimeInterval(600, 720));
        Booking a = assertInstanceOf(BookingResult.Confirmed.class, first).booking();
        assertInstanceOf(BookingResult.Waitlisted.class,
                svc.book(roomA, bob, new TimeInterval(600, 660)));
        assertInstanceOf(BookingResult.Waitlisted.class,
                svc.book(roomA, carol, new TimeInterval(660, 700)));

        svc.cancelBooking(a.id());

        List<Booking> bookings = svc.listBookings(roomA);
        assertEquals(1, bookings.size());
        assertEquals("bob", bookings.get(0).user().id());
        assertEquals(1, store.waitlistForRoom(roomA).size());
        assertEquals("carol", store.waitlistForRoom(roomA).get(0).user().id());
    }

    // S5: 跳过仍冲突的候补者
    @Test
    void skipsWaiterThatStillConflicts() {
        newService();
        BookingResult first = svc.book(roomA, alice, new TimeInterval(600, 660));
        Booking a = assertInstanceOf(BookingResult.Confirmed.class, first).booking();
        svc.book(roomA, dave, new TimeInterval(700, 760));
        assertInstanceOf(BookingResult.Waitlisted.class,
                svc.book(roomA, bob, new TimeInterval(630, 730)));
        assertInstanceOf(BookingResult.Waitlisted.class,
                svc.book(roomA, carol, new TimeInterval(610, 650)));

        svc.cancelBooking(a.id());

        List<Booking> bookings = svc.listBookings(roomA);
        assertEquals(2, bookings.size());
        assertTrue(bookings.stream().anyMatch(b -> b.user().id().equals("carol")));
        assertTrue(bookings.stream().anyMatch(b -> b.user().id().equals("dave")));
        assertEquals(1, store.waitlistForRoom(roomA).size());
        assertEquals("bob", store.waitlistForRoom(roomA).get(0).user().id());
    }

    // S6: 不跨房间晋升
    @Test
    void cancelDoesNotPromoteAcrossRooms() {
        newService();
        BookingResult aRes = svc.book(roomA, alice, new TimeInterval(600, 660));
        Booking a = assertInstanceOf(BookingResult.Confirmed.class, aRes).booking();

        // B 房：Dave 确认，Bob 候补
        svc.book(roomB, dave, new TimeInterval(600, 660));
        svc.book(roomB, bob, new TimeInterval(630, 700));

        // 取消 A 房的 Alice
        svc.cancelBooking(a.id());

        // 核心断言：B 房状态不变
        assertEquals(1, store.waitlistForRoom(roomB).size());
        assertEquals("bob", store.waitlistForRoom(roomB).get(0).user().id());

        List<Booking> bBookings = svc.listBookings(roomB);
        assertEquals(1, bBookings.size());
        assertEquals("dave", bBookings.get(0).user().id());
    }
}