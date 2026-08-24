package com.project.ticketflow.service.impl;

import com.project.ticketflow.dto.auth.AuthenticatedUser;
import com.project.ticketflow.dto.booking.BookingResponseDto;
import com.project.ticketflow.dto.booking.BookingSeatDto;
import com.project.ticketflow.dto.waitlist.JoinWaitlistRequestDto;
import com.project.ticketflow.dto.waitlist.WaitlistEntryResponseDto;
import com.project.ticketflow.entity.*;
import com.project.ticketflow.enums.OfferStatus;
import com.project.ticketflow.enums.SeatStatus;
import com.project.ticketflow.enums.WaitlistStatus;
import com.project.ticketflow.exception.BadRequestException;
import com.project.ticketflow.exception.OfferExpiredException;
import com.project.ticketflow.exception.ResourceNotFoundException;
import com.project.ticketflow.repository.*;
import com.project.ticketflow.security.SecurityHelper;
import com.project.ticketflow.service.WaitlistService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WaitlistServiceImpl implements WaitlistService {

    @Value("${waitlist.accumulation-timeout-minutes:60}")
    private long accumulationTimeoutMinutes;

    private static final int CRON_BATCH_SIZE = 50;

    private final ShowRepository showRepository;
    private final SeatCategoryRepository seatCategoryRepository;
    private final ShowSeatRepository showSeatRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final SeatOfferRepository seatOfferRepository;
    private final BookingHoldFactory bookingHoldFactory;
    private final SeatReleaseService seatReleaseService;
    private final SecurityHelper securityHelper;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    @Override
    @Transactional
    public WaitlistEntryResponseDto join(JoinWaitlistRequestDto requestDto) {
        AuthenticatedUser customer = currentUser();
        Show show = showRepository.findById(requestDto.getShowId())
                .orElseThrow(() -> new ResourceNotFoundException("Show not found with id: " + requestDto.getShowId()));
        SeatCategory category = seatCategoryRepository.findById(requestDto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + requestDto.getCategoryId()));

        if (hasShowPassed(show)) {
            throw new BadRequestException("This show has already taken place — the waitlist is closed");
        }

        long available = showSeatRepository.countByShowIdAndCategoryIdAndStatus(
                show.getId(), category.getId(), SeatStatus.AVAILABLE);
        if (available >= requestDto.getQuantity()) {
            throw new BadRequestException(
                    "Enough seats are still directly available in this category — no need to join the waitlist");
        }

        if (waitlistEntryRepository.existsByCustomerIdAndShowIdAndCategoryIdAndStatus(
                customer.getId(), show.getId(), category.getId(), WaitlistStatus.WAITING)) {
            throw new BadRequestException("You are already on the waitlist for this show and category");
        }

        WaitlistEntry entry = WaitlistEntry.builder()
                .customer(entityManager.getReference(User.class, customer.getId()))
                .show(show)
                .category(category)
                .status(WaitlistStatus.WAITING)
                .requestedQuantity(requestDto.getQuantity())
                .joinedAt(LocalDateTime.now())
                .build();
        WaitlistEntry saved = waitlistEntryRepository.save(entry);
        log.info("Customer {} joined waitlist {} for show {} category {} wanting {} seat(s)",
                customer.getId(), saved.getId(), show.getId(), category.getId(), requestDto.getQuantity());
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WaitlistEntryResponseDto> getMyEntries(Pageable pageable) {
        AuthenticatedUser customer = currentUser();
        return waitlistEntryRepository.findByCustomerIdOrderByJoinedAtDesc(customer.getId(), pageable)
                .map(this::toDto);
    }

    @Override
    @Transactional
    public BookingResponseDto acceptOffer(String token) {
        AuthenticatedUser customer = currentUser();
        List<SeatOffer> offers = seatOfferRepository.findAllByTokenForUpdate(token);
        if (offers.isEmpty()) {
            throw new ResourceNotFoundException("Offer not found");
        }

        WaitlistEntry entry = offers.get(0).getWaitlistEntry();
        if (!entry.getCustomer().getId().equals(customer.getId())) {
            throw new AccessDeniedException("This offer does not belong to you");
        }

        List<Long> seatIds = offers.stream().map(o -> o.getShowSeat().getId()).toList();
        List<ShowSeat> seats = showSeatRepository.findByIdInForUpdate(seatIds);

        for (SeatOffer offer : offers) {
            if (offer.getStatus() != OfferStatus.PENDING || offer.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new OfferExpiredException("This offer is no longer valid");
            }
        }
        for (ShowSeat seat : seats) {
            if (seat.getStatus() != SeatStatus.OFFERED) {
                throw new OfferExpiredException("This offer is no longer valid");
            }
        }

        Show show = seats.get(0).getShow();
        User customerRef = entityManager.getReference(User.class, customer.getId());
        Booking booking = bookingHoldFactory.createHeldBooking(customerRef, show, seats);

        for (SeatOffer offer : offers) {
            offer.setStatus(OfferStatus.ACCEPTED);
        }
        seatOfferRepository.saveAll(offers);

        entry.setStatus(WaitlistStatus.FULFILLED);
        waitlistEntryRepository.save(entry);

        log.info("Waitlist offer group {} ({} seats) accepted by customer {}, created booking {}",
                token, seats.size(), customer.getId(), booking.getId());
        return toBookingDto(booking, seats);
    }

    /**
     * Gives up on anyone who's waited too long without their quantity ever fully
     * accumulating (see SeatReleaseService — a large request can otherwise sit WAITING
     * indefinitely if enough seats never free up together). Once a category's queue is
     * empty, any seats left sitting in its shared pool get released back to AVAILABLE.
     */
    @Scheduled(fixedDelayString = "PT5M")
    @SchedulerLock(name = "expireStuckWaitlistEntriesTask", lockAtLeastFor = "PT10S", lockAtMostFor = "PT2M")
    public void expireStuckWaitlistEntries() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(accumulationTimeoutMinutes);
        int totalExpired = 0;
        boolean hasMore = true;

        while (hasMore) {
            Integer batchCount = transactionTemplate.execute(status -> {
                Pageable limit = PageRequest.of(0, CRON_BATCH_SIZE);
                List<WaitlistEntry> stuck = waitlistEntryRepository.findByStatusAndJoinedAtBefore(
                        WaitlistStatus.WAITING, threshold, limit);

                if (stuck.isEmpty()) return 0;

                for (WaitlistEntry entry : stuck) {
                    try {
                        entry.setStatus(WaitlistStatus.EXPIRED);
                        waitlistEntryRepository.save(entry);
                        // removing this entry might empty the queue out entirely, which is
                        // what actually lets any leftover pooled seats drain back out
                        seatReleaseService.tryFulfillQueue(entry.getShow(), entry.getCategory());
                    } catch (Exception e) {
                        log.error("Error expiring stuck waitlist entry {}", entry.getId(), e);
                    }
                }
                return stuck.size();
            });

            if (batchCount == null || batchCount == 0) {
                hasMore = false;
            } else {
                totalExpired += batchCount;
            }
            if (totalExpired > 2000) break;
        }

        if (totalExpired > 0) {
            log.info("Expired {} stuck waitlist entr(y/ies) past the {}-minute accumulation timeout",
                    totalExpired, accumulationTimeoutMinutes);
        }
    }

    private AuthenticatedUser currentUser() {
        return securityHelper.getCurrentAuthenticatedUser()
                .orElseThrow(() -> new AccessDeniedException("Cannot identify the authenticated user"));
    }

    private boolean hasShowPassed(Show show) {
        LocalDate today = LocalDate.now();
        LocalTime nowTime = LocalTime.now();
        return show.getShowDate().isBefore(today)
                || (show.getShowDate().isEqual(today) && show.getShowTime().isBefore(nowTime));
    }

    private WaitlistEntryResponseDto toDto(WaitlistEntry entry) {
        return WaitlistEntryResponseDto.builder()
                .id(entry.getId())
                .showId(entry.getShow().getId())
                .eventTitle(entry.getShow().getEvent().getTitle())
                .categoryId(entry.getCategory().getId())
                .categoryName(entry.getCategory().getName())
                .requestedQuantity(entry.getRequestedQuantity())
                .status(entry.getStatus().name())
                .joinedAt(entry.getJoinedAt())
                .build();
    }

    private BookingResponseDto toBookingDto(Booking booking, List<ShowSeat> seats) {
        List<BookingSeatDto> seatDtos = seats.stream()
                .map(seat -> BookingSeatDto.builder()
                        .seatLabel(seat.getSeat().getLabel())
                        .categoryName(seat.getCategory().getName())
                        .price(seat.getPrice())
                        .build())
                .toList();

        return BookingResponseDto.builder()
                .id(booking.getId())
                .showId(booking.getShow().getId())
                .eventTitle(booking.getShow().getEvent().getTitle())
                .status(booking.getStatus().name())
                .bookingReference(booking.getBookingReference())
                .customerName(booking.getCustomerName())
                .customerEmail(booking.getCustomerEmail())
                .customerPhone(booking.getCustomerPhone())
                .amount(booking.getAmount())
                .holdExpiresAt(booking.getHoldExpiresAt())
                .seats(seatDtos)
                .build();
    }
}
