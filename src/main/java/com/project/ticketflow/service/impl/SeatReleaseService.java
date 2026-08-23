package com.project.ticketflow.service.impl;

import com.project.ticketflow.entity.SeatCategory;
import com.project.ticketflow.entity.SeatOffer;
import com.project.ticketflow.entity.Show;
import com.project.ticketflow.entity.ShowSeat;
import com.project.ticketflow.entity.WaitlistEntry;
import com.project.ticketflow.enums.OfferStatus;
import com.project.ticketflow.enums.SeatStatus;
import com.project.ticketflow.enums.WaitlistStatus;
import com.project.ticketflow.repository.SeatOfferRepository;
import com.project.ticketflow.repository.ShowSeatRepository;
import com.project.ticketflow.repository.WaitlistEntryRepository;
import com.project.ticketflow.service.event.SeatOfferCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The single "a seat just became free" mechanism, and the group-accumulation math for a
 * quantity-based waitlist.
 *
 * <p>Freed seats for a (show, category) with an active waitlist don't go straight back to
 * AVAILABLE — they drop into a shared, anonymous RESERVED pool for that category. Nobody
 * "owns" a pooled seat. Whenever the pool changes, {@link #tryFulfillQueue} walks the WAITING
 * queue in join order and, for each entry, checks whether the pool can now cover its
 * {@code requestedQuantity} in one shot:
 * <ul>
 *   <li>if yes — pull exactly that many seats out, bundle them into one offer (one token,
 *   one {@code SeatOffer} row per seat), mark the entry OFFERED, and move on to the next
 *   entry against whatever pool remains;</li>
 *   <li>if no — leave that entry WAITING and keep going, so a smaller request further back
 *   in the queue can still be satisfied immediately ("skip-ahead" fairness) instead of being
 *   blocked behind a large request that hasn't accumulated enough yet.</li>
 * </ul>
 * Any pool left over after one full pass just stays RESERVED, waiting for the next seat to
 * free (or for {@code WaitlistServiceImpl#expireStuckWaitlistEntries} to eventually drain it
 * back to AVAILABLE once the queue for that category empties out).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeatReleaseService {

    @Value("${waitlist.offer.ttl-minutes:15}")
    private long offerTtlMinutes;

    private final WaitlistEntryRepository waitlistEntryRepository;
    private final SeatOfferRepository seatOfferRepository;
    private final ShowSeatRepository showSeatRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Caller must already hold a PESSIMISTIC_WRITE lock on {@code seat} (via
     * ShowSeatRepository#findByIdForUpdate / findByIdInForUpdate) within its own transaction.
     */
    @Transactional
    public void freeSeat(ShowSeat seat) {
        Show show = seat.getShow();
        SeatCategory category = seat.getCategory();

        boolean hasQueue = waitlistEntryRepository.existsWaiting(show.getId(), category.getId());
        if (!hasQueue) {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setHeldByBookingId(null);
            seat.setHoldExpiresAt(null);
            showSeatRepository.save(seat);
            return;
        }

        seat.setStatus(SeatStatus.RESERVED);
        seat.setHeldByBookingId(null);
        seat.setHoldExpiresAt(null);
        showSeatRepository.save(seat);

        tryFulfillQueue(show, category);
    }

    /**
     * Re-evaluates one category's waitlist queue against its current pool. Safe to call
     * whenever either side of that match could have changed (a seat just got pooled, or an
     * entry just left the queue).
     */
    @Transactional
    public void tryFulfillQueue(Show show, SeatCategory category) {
        List<WaitlistEntry> queue = waitlistEntryRepository.findAllWaitingForUpdate(show.getId(), category.getId());
        if (queue.isEmpty()) {
            drainPoolToAvailable(show, category);
            return;
        }

        List<ShowSeat> pool = showSeatRepository.findByShowIdAndCategoryIdAndStatusForUpdate(
                show.getId(), category.getId(), SeatStatus.RESERVED);
        int poolIndex = 0;

        for (WaitlistEntry entry : queue) {
            int remainingPool = pool.size() - poolIndex;
            if (remainingPool < entry.getRequestedQuantity()) {
                // not enough for this entry yet — skip it, let a smaller one behind it try
                continue;
            }
            List<ShowSeat> toOffer = pool.subList(poolIndex, poolIndex + entry.getRequestedQuantity());
            poolIndex += entry.getRequestedQuantity();
            finalizeOffer(entry, toOffer);
        }
        // whatever's left in pool[poolIndex:] simply stays RESERVED
    }

    private void finalizeOffer(WaitlistEntry entry, List<ShowSeat> seats) {
        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(offerTtlMinutes);

        List<SeatOffer> offers = new ArrayList<>(seats.size());
        for (ShowSeat seat : seats) {
            seat.setStatus(SeatStatus.OFFERED);
            seat.setHoldExpiresAt(expiresAt);
            showSeatRepository.save(seat);

            offers.add(SeatOffer.builder()
                    .waitlistEntry(entry)
                    .showSeat(seat)
                    .token(token)
                    .expiresAt(expiresAt)
                    .status(OfferStatus.PENDING)
                    .build());
        }
        seatOfferRepository.saveAll(offers);

        entry.setStatus(WaitlistStatus.OFFERED);
        waitlistEntryRepository.save(entry);

        log.info("Offered {} seat(s) to waitlist entry {} (offer group {})", seats.size(), entry.getId(), token);
        eventPublisher.publishEvent(new SeatOfferCreatedEvent(token));
    }

    /**
     * Once a category's queue is empty, any seats still sitting in its RESERVED pool aren't
     * doing anything for anyone — release them back to general availability.
     */
    private void drainPoolToAvailable(Show show, SeatCategory category) {
        List<ShowSeat> pool = showSeatRepository.findByShowIdAndCategoryIdAndStatusForUpdate(
                show.getId(), category.getId(), SeatStatus.RESERVED);
        if (pool.isEmpty()) return;

        for (ShowSeat seat : pool) {
            seat.setStatus(SeatStatus.AVAILABLE);
        }
        showSeatRepository.saveAll(pool);
        log.info("Drained {} unclaimed pooled seat(s) back to AVAILABLE for show {} category {} (queue empty)",
                pool.size(), show.getId(), category.getId());
    }
}
