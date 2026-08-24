package com.project.ticketflow.service.impl;

import com.project.ticketflow.dto.auth.AuthenticatedUser;
import com.project.ticketflow.dto.booking.BookingResponseDto;
import com.project.ticketflow.dto.booking.BookingSeatDto;
import com.project.ticketflow.dto.booking.ConfirmBookingRequestDto;
import com.project.ticketflow.dto.booking.HoldSeatsRequestDto;
import com.project.ticketflow.entity.*;
import com.project.ticketflow.enums.BookingStatus;
import com.project.ticketflow.enums.OfferStatus;
import com.project.ticketflow.enums.SeatStatus;
import com.project.ticketflow.enums.WaitlistStatus;
import com.project.ticketflow.exception.BadRequestException;
import com.project.ticketflow.exception.CustomException;
import com.project.ticketflow.exception.ResourceNotFoundException;
import com.project.ticketflow.exception.SeatUnavailableException;
import com.project.ticketflow.repository.*;
import com.project.ticketflow.security.SecurityHelper;
import com.project.ticketflow.service.BookingService;
import com.project.ticketflow.service.event.BookingConfirmedEvent;
import com.project.ticketflow.service.event.SeatStatusChangedEvent;
import com.project.ticketflow.util.QrCodeGenerator;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final BookingRepository bookingRepository;
    private final SeatOfferRepository seatOfferRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final BookingHoldFactory bookingHoldFactory;
    private final SeatReleaseService seatReleaseService;
    private final ApplicationEventPublisher eventPublisher;
    private final SecurityHelper securityHelper;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;
    private final QrCodeGenerator qrCodeGenerator;

    private static final int CRON_BATCH_SIZE = 50;
    private static final List<BookingStatus> BOOKING_HISTORY_STATUSES =
            List.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED);

    @Override
    @Transactional
    public BookingResponseDto holdSeats(HoldSeatsRequestDto requestDto) {
        log.info("Holding {} seat(s) for show {}", requestDto.getShowSeatIds().size(), requestDto.getShowId());
        Show show = showRepository.findById(requestDto.getShowId())
                .orElseThrow(() -> new ResourceNotFoundException("Show not found with id: " + requestDto.getShowId()));

        if (hasShowPassed(show)) {
            throw new BadRequestException("This show has already taken place — seats can no longer be held");
        }

        List<ShowSeat> lockedSeats;
        try {
            lockedSeats = showSeatRepository.findByIdInForUpdate(requestDto.getShowSeatIds());
        } catch (PessimisticLockingFailureException e) {
            log.warn("Failed to acquire lock for seats {}", requestDto.getShowSeatIds());
            throw new SeatUnavailableException("Some of the selected seats are being booked by another customer. Please try again.");
        }

        if (lockedSeats.size() != requestDto.getShowSeatIds().size()) {
            throw new ResourceNotFoundException("One or more selected seats do not exist");
        }

        for (ShowSeat seat : lockedSeats) {
            if (!seat.getShow().getId().equals(requestDto.getShowId())) {
                throw new BadRequestException("Seat " + seat.getId() + " does not belong to show " + requestDto.getShowId());
            }
            if (seat.getStatus() != SeatStatus.AVAILABLE) {
                throw new SeatUnavailableException("Seat " + seat.getSeat().getLabel() + " is no longer available");
            }
        }

        AuthenticatedUser authenticatedUser = currentUser();
        User customerRef = entityManager.getReference(User.class, authenticatedUser.getId());

        Booking booking = bookingHoldFactory.createHeldBooking(customerRef, show, lockedSeats);
        log.info("Held booking {} for customer {}", booking.getId(), authenticatedUser.getId());
        return toDto(booking, lockedSeats);
    }

    @Override
    @Transactional
    public BookingResponseDto confirmBooking(Long bookingId, ConfirmBookingRequestDto requestDto) {
        log.info("Confirming booking {}", bookingId);
        Booking booking = getBookingWithSeatsOrThrow(bookingId);
        verifyOwner(booking);

        if (booking.getStatus() != BookingStatus.HELD) {
            throw new CustomException("Booking is in status " + booking.getStatus() + ", cannot confirm", HttpStatus.CONFLICT);
        }
        if (booking.getHoldExpiresAt() == null || booking.getHoldExpiresAt().isBefore(LocalDateTime.now())) {
            throw new CustomException("Seat hold has expired. Please select seats again.", HttpStatus.CONFLICT);
        }

        List<Long> seatIds = booking.getSeats().stream().map(bs -> bs.getShowSeat().getId()).toList();
        List<ShowSeat> seats = showSeatRepository.findByIdInForUpdate(seatIds);
        for (ShowSeat seat : seats) {
            seat.setStatus(SeatStatus.BOOKED);
            seat.setHoldExpiresAt(null);
        }
        showSeatRepository.saveAll(seats);
        for (ShowSeat seat : seats) {
            eventPublisher.publishEvent(new SeatStatusChangedEvent(booking.getShow().getId(), seat.getId(), SeatStatus.BOOKED.name()));
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setHoldExpiresAt(null);
        booking.setCustomerName(requestDto.getCustomerName());
        booking.setCustomerEmail(requestDto.getCustomerEmail());
        booking.setCustomerPhone(requestDto.getCustomerPhone());
        booking.setBookingReference(generateBookingReference());
        Booking savedBooking = bookingRepository.save(booking);

        eventPublisher.publishEvent(new BookingConfirmedEvent(savedBooking.getId()));
        log.info("Confirmed booking {} with reference {}", savedBooking.getId(), savedBooking.getBookingReference());
        return toDto(savedBooking, seats);
    }

    @Override
    @Transactional
    public BookingResponseDto cancelBooking(Long bookingId) {
        log.info("Cancelling booking {}", bookingId);
        Booking booking = getBookingWithSeatsOrThrow(bookingId);
        verifyOwner(booking);

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return toDto(booking, seatsOf(booking));
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BadRequestException("Only a confirmed booking can be cancelled");
        }
        if (hasShowPassed(booking.getShow())) {
            throw new BadRequestException("Cannot cancel a booking for a show that has already started");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        Booking savedBooking = bookingRepository.save(booking);

        List<Long> seatIds = booking.getSeats().stream().map(bs -> bs.getShowSeat().getId()).toList();
        List<ShowSeat> seats = showSeatRepository.findByIdInForUpdate(seatIds);
        for (ShowSeat seat : seats) {
            seatReleaseService.freeSeat(seat);
        }

        log.info("Cancelled booking {} and released {} seat(s)", bookingId, seats.size());
        return toDto(savedBooking, seats);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookingResponseDto> getMyBookings(Pageable pageable) {
        AuthenticatedUser customer = currentUser();
        return bookingRepository.findByCustomerIdAndStatusInOrderByCreatedAtDesc(
                        customer.getId(), BOOKING_HISTORY_STATUSES, pageable)
                .map(booking -> toDto(booking, seatsOf(booking)));
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponseDto getBookingById(Long bookingId) {
        Booking booking = getBookingWithSeatsOrThrow(bookingId);
        verifyOwnerOrOrganiser(booking);
        return toDto(booking, seatsOf(booking));
    }

    @Override
    @Scheduled(fixedDelayString = "PT30S")
    @SchedulerLock(name = "cleanUpExpiredHoldsAndOffersTask", lockAtLeastFor = "PT10S", lockAtMostFor = "PT2M")
    public void cleanUpExpiredHoldsAndOffers() {
        long start = System.currentTimeMillis();
        int expiredBookings = expireHeldBookings();
        int expiredOffers = expireSeatOffers();
        if (expiredBookings > 0 || expiredOffers > 0) {
            log.info("Cleanup: expired {} booking hold(s), {} seat offer(s) (took {}ms)",
                    expiredBookings, expiredOffers, System.currentTimeMillis() - start);
        }
    }

    private int expireHeldBookings() {
        LocalDateTime now = LocalDateTime.now();
        int totalProcessed = 0;
        boolean hasMore = true;

        while (hasMore) {
            Integer batchCount = transactionTemplate.execute(status -> {
                Pageable limit = PageRequest.of(0, CRON_BATCH_SIZE);
                List<Booking> expired = bookingRepository.findByStatusAndHoldExpiresAtBefore(
                        BookingStatus.HELD, now, limit);

                if (expired.isEmpty()) return 0;

                for (Booking booking : expired) {
                    try {
                        // lazy-loads within this still-open transaction
                        List<Long> seatIds = booking.getSeats().stream().map(bs -> bs.getShowSeat().getId()).toList();
                        List<ShowSeat> seats = showSeatRepository.findByIdInForUpdate(seatIds);
                        for (ShowSeat seat : seats) {
                            seatReleaseService.freeSeat(seat);
                        }
                        booking.setStatus(BookingStatus.EXPIRED);
                        booking.setHoldExpiresAt(null);
                        bookingRepository.save(booking);
                    } catch (Exception e) {
                        log.error("Error expiring booking hold {}", booking.getId(), e);
                    }
                }

                entityManager.flush();
                entityManager.clear();
                return expired.size();
            });

            if (batchCount == null || batchCount == 0) {
                hasMore = false;
            } else {
                totalProcessed += batchCount;
            }
            if (totalProcessed > 2000) break;
        }
        return totalProcessed;
    }

    private int expireSeatOffers() {
        LocalDateTime now = LocalDateTime.now();
        int totalProcessed = 0;
        boolean hasMore = true;

        while (hasMore) {
            Integer batchCount = transactionTemplate.execute(status -> {
                Pageable limit = PageRequest.of(0, CRON_BATCH_SIZE);
                List<SeatOffer> expired = seatOfferRepository.findByStatusAndExpiresAtBefore(
                        OfferStatus.PENDING, now, limit);

                if (expired.isEmpty()) return 0;

                for (SeatOffer offer : expired) {
                    try {
                        ShowSeat seat = showSeatRepository.findByIdForUpdate(offer.getShowSeat().getId());
                        // only cascade if the seat is still sitting in this offer's OFFERED state
                        // (guards against a rare double-processing of the same expired offer)
                        if (seat.getStatus() == SeatStatus.OFFERED) {
                            seatReleaseService.freeSeat(seat);
                        }
                        offer.setStatus(OfferStatus.EXPIRED);
                        seatOfferRepository.save(offer);

                        // one row per seat in the group shares this entry — only the first
                        // row processed for a given group actually needs to flip it, but
                        // re-setting the same value on the others is harmless
                        WaitlistEntry entry = offer.getWaitlistEntry();
                        if (entry.getStatus() == WaitlistStatus.OFFERED) {
                            entry.setStatus(WaitlistStatus.EXPIRED);
                            waitlistEntryRepository.save(entry);
                        }
                    } catch (Exception e) {
                        log.error("Error expiring seat offer {}", offer.getId(), e);
                    }
                }

                entityManager.flush();
                entityManager.clear();
                return expired.size();
            });

            if (batchCount == null || batchCount == 0) {
                hasMore = false;
            } else {
                totalProcessed += batchCount;
            }
            if (totalProcessed > 2000) break;
        }
        return totalProcessed;
    }

    private Booking getBookingWithSeatsOrThrow(Long bookingId) {
        return bookingRepository.findByIdWithSeats(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));
    }

    private List<ShowSeat> seatsOf(Booking booking) {
        if (booking.getSeats() == null) return List.of();
        return booking.getSeats().stream().map(BookingSeat::getShowSeat).toList();
    }

    private String generateBookingReference() {
        return "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private boolean hasShowPassed(Show show) {
        LocalDate today = LocalDate.now();
        LocalTime nowTime = LocalTime.now();
        return show.getShowDate().isBefore(today)
                || (show.getShowDate().isEqual(today) && show.getShowTime().isBefore(nowTime));
    }

    private AuthenticatedUser currentUser() {
        return securityHelper.getCurrentAuthenticatedUser()
                .orElseThrow(() -> new AccessDeniedException("Cannot identify the authenticated user"));
    }

    private void verifyOwner(Booking booking) {
        AuthenticatedUser currentUser = currentUser();
        if (!currentUser.getId().equals(booking.getCustomer().getId())) {
            throw new AccessDeniedException("Booking does not belong to the authenticated user");
        }
    }

    private void verifyOwnerOrOrganiser(Booking booking) {
        AuthenticatedUser currentUser = currentUser();
        boolean isCustomer = currentUser.getId().equals(booking.getCustomer().getId());
        boolean isOrganiser = currentUser.getId().equals(booking.getShow().getEvent().getOrganiser().getId());
        if (!isCustomer && !isOrganiser) {
            throw new AccessDeniedException("You are not authorized to view this booking");
        }
    }

    private BookingResponseDto toDto(Booking booking, List<ShowSeat> seats) {
        List<BookingSeatDto> seatDtos = seats.stream()
                .map(seat -> BookingSeatDto.builder()
                        .seatLabel(seat.getSeat().getLabel())
                        .categoryName(seat.getCategory().getName())
                        .price(seat.getPrice())
                        .build())
                .toList();

        String qrCodeBase64 = booking.getBookingReference() == null
                ? null
                : qrCodeGenerator.generateBase64Png(booking.getBookingReference());

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
                .qrCodeBase64(qrCodeBase64)
                .seats(seatDtos)
                .build();
    }
}
