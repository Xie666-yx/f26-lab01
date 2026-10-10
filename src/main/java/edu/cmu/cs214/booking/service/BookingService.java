package edu.cmu.cs214.booking.service;

import edu.cmu.cs214.booking.domain.Booking;
import edu.cmu.cs214.booking.domain.Room;
import edu.cmu.cs214.booking.domain.TimeInterval;
import edu.cmu.cs214.booking.domain.User;
import edu.cmu.cs214.booking.domain.WaitlistEntry;
import edu.cmu.cs214.booking.repo.BookingStore;
import java.util.List;

/**
 * Coordinates bookings and the waitlist. Enforces the core invariant: a room
 * never holds two confirmed bookings whose intervals overlap. Persistence is
 * delegated to a {@link BookingStore}.
 */
public class BookingService {

    private final BookingStore store;
    private int nextBookingSeq = 1;
    private int nextWaitlistSeq = 1;

    public BookingService(BookingStore store) {
        this.store = store;
    }

    /**
     * Attempts to book {@code room} for {@code user} over {@code interval}. If the
     * room is free over that interval the booking is confirmed; otherwise the user
     * is placed on the room's waitlist.
     */
    public BookingResult book(Room room, User user, TimeInterval interval) {
        for (Booking existing : store.bookingsForRoom(room)) {
            if (existing.interval().overlaps(interval)) {
                int position = store.waitlistForRoom(room).size() + 1;
                int seq = nextWaitlistSeq++;
                store.addWaitlistEntry(new WaitlistEntry("w" + seq, room, user, interval, seq));
                return new BookingResult.Waitlisted(position);
            }
        }
        Booking booking = new Booking("b" + nextBookingSeq++, room, user, interval);
        store.addBooking(booking);
        return new BookingResult.Confirmed(booking);
    }

    /** Returns the confirmed bookings for {@code room}. */
    public List<Booking> listBookings(Room room) {
        return store.bookingsForRoom(room);
    }

    /**
     * Cancels the confirmed booking with the given id.
     * If the booking does not exist, does nothing.
     * After removal, promotes at most one waitlisted user for the same room,
     * in seq order, whose interval does not overlap any remaining confirmed booking.
     */
    public void cancelBooking(String bookingId) {
        java.util.Optional<Booking> found = store.findBooking(bookingId);
        if (found.isEmpty()) {
            return;
        }
        Booking cancelled = found.get();
        Room room = cancelled.room();

        store.removeBooking(bookingId);

        java.util.List<Booking> remaining = store.bookingsForRoom(room);

        java.util.List<WaitlistEntry> waitlist =
                new java.util.ArrayList<>(store.waitlistForRoom(room));
        waitlist.sort(java.util.Comparator.comparingInt(WaitlistEntry::seq));

        for (WaitlistEntry entry : waitlist) {
            boolean conflicts = false;
            for (Booking b : remaining) {
                if (b.interval().overlaps(entry.interval())) {
                    conflicts = true;
                    break;
                }
            }
            if (conflicts) {
                continue;
            }

            store.removeWaitlistEntry(entry.id());
            Booking promoted = new Booking(
                    "b" + nextBookingSeq++,
                    entry.room(),
                    entry.user(),
                    entry.interval()
            );
            store.addBooking(promoted);
            break;
        }
    }
}
