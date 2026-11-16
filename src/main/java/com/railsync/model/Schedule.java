package com.railsync.model;

import com.railsync.exception.PlatformUnavailableException;
import com.railsync.exception.RailSyncException;
import com.railsync.exception.SchedulingConflictException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The station's timetable of platform bookings: which train is on which
 * platform, and when.
 *
 * <p>Why it exists: it is the single place that creates and deletes
 * reservations, and it keeps each Train's assigned platform in step with
 * its reservation. Nothing else can change bookings.</p>
 *
 * <p>OOP concepts:</p>
 * <ul>
 *   <li>Composition: Schedule creates, owns and deletes its
 *       PlatformReservation objects.</li>
 *   <li>Encapsulation: the reservation collections are private; callers get
 *       read-only lists. The clearance buffer is changed only through a
 *       validated setter.</li>
 *   <li>Constructor chaining: the no-argument constructor calls this(5).</li>
 * </ul>
 *
 * <p>Collections:</p>
 * <ul>
 *   <li>ArrayList - all reservations, in the order they were made.</li>
 *   <li>HashMap (train id to reservation) - answers "where is train X?"
 *       in one step, which delay handling and reports ask often.</li>
 * </ul>
 *
 * <p>Time rule: every reservation is a half-open interval [start, end), and
 * two reservations on the same platform must also be separated by a
 * clearance gap. See findConflict() for the exact rule and a worked example.</p>
 *
 * <p>Works with: Train, Platform, PlatformReservation.</p>
 */
public class Schedule {

    /** Default clearance gap kept free around every reservation, in minutes. */
    public static final int DEFAULT_CLEARANCE_BUFFER_MINUTES = 5;
    /** Largest buffer we allow, to catch typing mistakes. */
    public static final int MAX_CLEARANCE_BUFFER_MINUTES = 60;

    // [COMPOSITION] Schedule creates and owns its reservations.
    private final List<PlatformReservation> reservations = new ArrayList<>();
    private final Map<String, PlatformReservation> reservationByTrainId = new HashMap<>();
    private int clearanceBufferMinutes;

    /** Creates an empty schedule with the default 5-minute clearance buffer. */
    public Schedule() {
        this(DEFAULT_CLEARANCE_BUFFER_MINUTES);
    }

    /**
     * Creates an empty schedule with a chosen clearance buffer.
     *
     * @param clearanceBufferMinutes 0 to 60 minutes
     * @throws RailSyncException if the buffer is out of range
     */
    public Schedule(int clearanceBufferMinutes) {
        setClearanceBufferMinutes(clearanceBufferMinutes);
    }

    /**
     * Books a platform for a train, starting at the given time and lasting
     * for the train's dwell time.
     *
     * @param train    the train to place
     * @param platform the platform to use
     * @param start    when the train starts occupying the platform; usually
     *                 its expected arrival, or later if it had to wait
     * @return the new reservation
     * @throws SchedulingConflictException  if the train already has a booking,
     *                                      start is before the train arrives, or
     *                                      the time clashes with another booking
     * @throws PlatformUnavailableException if the train cannot use this platform
     */
    public PlatformReservation reserve(Train train, Platform platform, LocalDateTime start) {
        // [SOLID-L] any Train subclass works here: the checks only use methods every Train has.
        // Step 1: a train can hold only one platform at a time.
        if (reservationByTrainId.containsKey(train.getId())) {
            throw new SchedulingConflictException("Train " + train.getNumber()
                    + " already has a platform. Release it first.");
        }
        // Step 2: the train cannot reach the platform before it arrives.
        if (start.isBefore(train.getExpectedArrival())) {
            throw new SchedulingConflictException("Train " + train.getNumber()
                    + " cannot occupy a platform before it arrives at "
                    + train.getExpectedArrival().toLocalTime() + ".");
        }
        // Step 3: the platform must be working, accept this category, and be long enough.
        // The rules live in Train, so they are written only once.
        String reason = train.getIncompatibilityReason(platform);
        if (reason != null) {
            throw new PlatformUnavailableException(reason);
        }
        // Step 4: the time slot (plus clearance gap) must be free on this platform.
        // This is the last line of defence: even a buggy strategy cannot double-book.
        PlatformReservation clash = findConflict(train, platform, start);
        if (clash != null) {
            throw new SchedulingConflictException("Train " + train.getNumber() + " at "
                    + start.toLocalTime() + " clashes with " + clash
                    + " (a " + requiredGapMinutes(clash.getTrain(), train)
                    + "-minute clearance gap is needed).");
        }
        // Step 5: create the reservation. It lasts exactly the train's dwell time.
        LocalDateTime end = start.plus(train.getDwellTime());
        PlatformReservation reservation = new PlatformReservation(train, platform, start, end);

        // Step 6: record it in both collections and link the train to the platform,
        // all in one place so they can never disagree.
        reservations.add(reservation);
        reservationByTrainId.put(train.getId(), reservation);
        train.assignPlatform(platform);
        return reservation;
    }

    // ------------------------------------------------------------------
    // Interval logic: half-open intervals plus a clearance gap.
    // ------------------------------------------------------------------

    /**
     * Checks whether two half-open time intervals [aStart, aEnd) and
     * [bStart, bEnd) share at least one minute.
     *
     * <p>Rule: they overlap when each one starts before the other one ends.
     * Because the end is exclusive, intervals that only touch do NOT overlap.
     * Example: [10:00, 10:30) and [10:30, 11:00) -> no overlap (10:30 is the
     * first free minute of A and the first busy minute of B).
     * [10:00, 10:30) and [10:29, 11:00) -> overlap at 10:29.</p>
     *
     * @param aStart start of A (inclusive)
     * @param aEnd   end of A (exclusive)
     * @param bStart start of B (inclusive)
     * @param bEnd   end of B (exclusive)
     * @return true if they overlap
     */
    public static boolean overlaps(LocalDateTime aStart, LocalDateTime aEnd,
                                   LocalDateTime bStart, LocalDateTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    /**
     * Returns the clearance gap (in minutes) that must separate two trains
     * on the same platform. The bigger of the two trains' needs wins, so a
     * freight train always gets its extra time whichever side it is on.
     *
     * @param first  one train
     * @param second the other train
     * @return gap in minutes
     */
    public int requiredGapMinutes(Train first, Train second) {
        // [POLYMORPHISM] each train's subclass decides its own buffer (FreightTrain asks for more).
        int firstNeeds = first.getClearanceBufferMinutes(clearanceBufferMinutes);
        int secondNeeds = second.getClearanceBufferMinutes(clearanceBufferMinutes);
        return Math.max(firstNeeds, secondNeeds);
    }

    /**
     * Looks for an existing booking that would clash if the train used the
     * platform from {@code start} for its dwell time.
     *
     * <p>How the clearance gap works: we stretch the END of both intervals by
     * the gap and then do a normal half-open overlap test. That is the same
     * as saying "the next train may start only gap minutes after the previous
     * one leaves".</p>
     *
     * <p>Worked example (gap 5 minutes):
     * A is booked [10:00, 10:30). B wants [10:30, 11:00).
     * Stretched: A = [10:00, 10:35), B = [10:30, 11:05). They overlap
     * (10:30 is before 10:35), so it is a conflict: B must start at 10:35 or
     * later. With B at 10:35 the stretched A ends exactly where B starts, so
     * there is no overlap and the booking is allowed.</p>
     *
     * <p>The train's own reservation (if any) is ignored, so this method can
     * also re-check a train after it is delayed.</p>
     *
     * @param train    the train that wants the platform
     * @param platform the platform
     * @param start    when the train would start occupying it
     * @return the first clashing reservation, or null if the slot is free
     */
    public PlatformReservation findConflict(Train train, Platform platform, LocalDateTime start) {
        for (PlatformReservation existing : reservations) {
            if (clashes(existing, train, platform, start)) {
                return existing;
            }
        }
        return null;
    }

    /**
     * Same rule as findConflict(), but returns EVERY clashing booking.
     * Preemption uses it to see whether exactly one train is in the way.
     *
     * @param train    the train that wants the platform
     * @param platform the platform
     * @param start    when the train would start occupying it
     * @return a new list of clashing reservations (empty if the slot is free)
     */
    public List<PlatformReservation> findAllConflicts(Train train, Platform platform, LocalDateTime start) {
        List<PlatformReservation> result = new ArrayList<>();
        for (PlatformReservation existing : reservations) {
            if (clashes(existing, train, platform, start)) {
                result.add(existing);
            }
        }
        return result;
    }

    /**
     * The single clash rule, shared by findConflict() and findAllConflicts()
     * so it is written only once.
     */
    private boolean clashes(PlatformReservation existing, Train train, Platform platform, LocalDateTime start) {
        // Step 1: only bookings on the same platform can clash.
        if (existing.getPlatform() != platform) {
            return false;
        }
        // Step 2: a train never clashes with its own booking.
        if (existing.getTrain() == train) {
            return false;
        }
        // Step 3: stretch both ends by the gap and test for overlap.
        LocalDateTime end = start.plus(train.getDwellTime());
        int gap = requiredGapMinutes(existing.getTrain(), train);
        return overlaps(start, end.plusMinutes(gap),
                existing.getStart(), existing.getEnd().plusMinutes(gap));
    }

    /**
     * Finds the earliest moment, at or after {@code notBefore}, when the train
     * could start on this platform without any clash. Compatibility (length,
     * category, failed) is NOT checked here; callers check that first.
     *
     * <p>Idea: try {@code notBefore}. If it clashes with booking X, no start
     * time before "X ends + gap" can work, so jump straight there and try
     * again. Each jump moves strictly later and there are only a few
     * bookings, so the loop always finishes.</p>
     *
     * <p>Example (gap 5): bookings [10:00,10:30) and [10:40,11:00); a train
     * with a 10-minute dwell asks from 10:10. 10:10 clashes with the first,
     * jump to 10:35. 10:35 clashes with the second (10:35+10+5 = 10:50 is
     * after 10:40), jump to 11:05. 11:05 is free, so the answer is 11:05.</p>
     *
     * @param train     the train
     * @param platform  the platform
     * @param notBefore the earliest time we are interested in
     * @return the earliest clash-free start time
     */
    public LocalDateTime earliestFreeStart(Train train, Platform platform, LocalDateTime notBefore) {
        LocalDateTime candidate = notBefore;
        PlatformReservation clash = findConflict(train, platform, candidate);
        while (clash != null) {
            // Step 1: every start before "clash end + gap" would still clash, so skip past it.
            candidate = clash.getEnd().plusMinutes(requiredGapMinutes(clash.getTrain(), train));
            // Step 2: check the new candidate against all bookings again.
            clash = findConflict(train, platform, candidate);
        }
        return candidate;
    }

    /**
     * Cancels a train's booking and unlinks it from its platform.
     *
     * @param train the train to release
     * @return true if the train had a booking
     */
    public boolean release(Train train) {
        PlatformReservation reservation = reservationByTrainId.remove(train.getId());
        if (reservation == null) {
            return false;
        }
        // [COMPOSITION] once removed here, the reservation no longer exists in the system.
        reservations.remove(reservation);
        train.clearPlatform();
        return true;
    }

    /**
     * Finds the booking of a train.
     *
     * @param train the train
     * @return its reservation, or null if it has none
     */
    public PlatformReservation findReservation(Train train) {
        return reservationByTrainId.get(train.getId());
    }

    /**
     * Returns the bookings on one platform, earliest first.
     *
     * @param platform the platform
     * @return a new list sorted by start time
     */
    public List<PlatformReservation> getReservationsOn(Platform platform) {
        // Step 1: collect the reservations that use this platform.
        List<PlatformReservation> result = new ArrayList<>();
        for (PlatformReservation reservation : reservations) {
            if (reservation.getPlatform() == platform) {
                result.add(reservation);
            }
        }
        // Step 2: sort by start time so callers can walk the platform's day in order.
        result.sort((a, b) -> a.getStart().compareTo(b.getStart()));
        return result;
    }

    /**
     * Checks whether any train is booked on a platform. Used to refuse
     * deleting a platform that still has bookings.
     *
     * @param platform the platform
     * @return true if at least one booking uses it
     */
    public boolean hasReservations(Platform platform) {
        for (PlatformReservation reservation : reservations) {
            if (reservation.getPlatform() == platform) {
                return true;
            }
        }
        return false;
    }

    /** @return all reservations, read-only */
    public List<PlatformReservation> getAllReservations() {
        return Collections.unmodifiableList(reservations);
    }

    /** @return number of reservations */
    public int size() {
        return reservations.size();
    }

    /** @return the station-wide clearance buffer in minutes */
    public int getClearanceBufferMinutes() {
        return clearanceBufferMinutes;
    }

    /**
     * Changes the station-wide clearance buffer (configurable, not hardcoded).
     *
     * @param minutes 0 to 60
     * @throws RailSyncException if out of range
     */
    public final void setClearanceBufferMinutes(int minutes) {
        if (minutes < 0 || minutes > MAX_CLEARANCE_BUFFER_MINUTES) {
            throw new RailSyncException("Clearance buffer must be between 0 and "
                    + MAX_CLEARANCE_BUFFER_MINUTES + " minutes, but was " + minutes + ".");
        }
        this.clearanceBufferMinutes = minutes;
    }
}
