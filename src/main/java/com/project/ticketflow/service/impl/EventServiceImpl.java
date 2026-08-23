package com.project.ticketflow.service.impl;

import com.project.ticketflow.dto.auth.AuthenticatedUser;
import com.project.ticketflow.dto.event.*;
import com.project.ticketflow.entity.*;
import com.project.ticketflow.enums.EventType;
import com.project.ticketflow.enums.SeatStatus;
import com.project.ticketflow.enums.ShowStatus;
import com.project.ticketflow.exception.BadRequestException;
import com.project.ticketflow.exception.ResourceNotFoundException;
import com.project.ticketflow.repository.*;
import com.project.ticketflow.security.SecurityHelper;
import com.project.ticketflow.service.EventService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final ShowRepository showRepository;
    private final VenueRepository venueRepository;
    private final SeatCategoryRepository seatCategoryRepository;
    private final SeatRepository seatRepository;
    private final ShowCategoryPriceRepository showCategoryPriceRepository;
    private final ShowSeatRepository showSeatRepository;
    private final BookingRepository bookingRepository;
    private final SecurityHelper securityHelper;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public EventResponseDto createEvent(EventRequestDto requestDto) {
        AuthenticatedUser organiser = currentUser();
        log.info("Creating event '{}' for organiser {}", requestDto.getTitle(), organiser.getId());

        EventType type;
        try {
            type = EventType.valueOf(requestDto.getType().trim().toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("Invalid event type: " + requestDto.getType());
        }

        Event event = Event.builder()
                .organiser(entityManager.getReference(User.class, organiser.getId()))
                .title(requestDto.getTitle())
                .type(type)
                .description(requestDto.getDescription())
                .active(true)
                .build();

        Event saved = eventRepository.save(event);
        return toDto(saved, List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponseDto getEvent(Long eventId) {
        Event event = getEventOrThrow(eventId);
        List<Show> shows = showRepository.findByEventOrderByShowDateAscShowTimeAsc(event);
        List<ShowResponseDto> showDtos = shows.stream().map(this::toShowDto).toList();
        return toDto(event, showDtos);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EventResponseDto> searchEvents(String type, String city, String query, Pageable pageable) {
        EventType eventType = null;
        if (type != null && !type.isBlank()) {
            try {
                eventType = EventType.valueOf(type.trim().toUpperCase());
            } catch (Exception e) {
                throw new BadRequestException("Invalid event type: " + type);
            }
        }
        String trimmedQuery = (query == null || query.isBlank()) ? null : query.trim();
        return eventRepository.searchEvents(eventType, city, trimmedQuery, pageable)
                .map(event -> toDto(event, List.of()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EventResponseDto> getMyEvents(Pageable pageable) {
        AuthenticatedUser organiser = currentUser();
        User organiserRef = entityManager.getReference(User.class, organiser.getId());
        return eventRepository.findByOrganiser(organiserRef, pageable)
                .map(event -> toDto(event, List.of()));
    }

    @Override
    @Transactional
    public ShowResponseDto createShow(Long eventId, ShowRequestDto requestDto) {
        Event event = getEventOrThrow(eventId);
        verifyOwner(event);

        Venue venue = venueRepository.findById(requestDto.getVenueId())
                .orElseThrow(() -> new ResourceNotFoundException("Venue not found with id: " + requestDto.getVenueId()));

        Show show = Show.builder()
                .event(event)
                .venue(venue)
                .showDate(requestDto.getShowDate())
                .showTime(requestDto.getShowTime())
                .status(ShowStatus.SCHEDULED)
                .build();
        Show savedShow = showRepository.save(show);

        // resolve per-category price and persist it
        List<ShowCategoryPrice> prices = new ArrayList<>();
        for (CategoryPriceDto priceDto : requestDto.getCategoryPrices()) {
            SeatCategory category = seatCategoryRepository.findByVenueAndName(venue, priceDto.getCategoryName())
                    .orElseThrow(() -> new BadRequestException(
                            "Venue has no seat category named: " + priceDto.getCategoryName()));
            prices.add(ShowCategoryPrice.builder()
                    .show(savedShow)
                    .category(category)
                    .price(priceDto.getPrice())
                    .build());
        }
        List<ShowCategoryPrice> savedPrices = showCategoryPriceRepository.saveAll(prices);

        // snapshot the venue's static seat map into per-show seats, each priced by its category
        List<Seat> venueSeats = seatRepository.findByVenue(venue);
        if (venueSeats.isEmpty()) {
            throw new BadRequestException("Venue has no seats configured yet");
        }

        List<ShowSeat> showSeats = new ArrayList<>();
        for (Seat seat : venueSeats) {
            BigDecimal price = savedPrices.stream()
                    .filter(p -> p.getCategory().getId().equals(seat.getCategory().getId()))
                    .map(ShowCategoryPrice::getPrice)
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException(
                            "No price set for category: " + seat.getCategory().getName()));

            showSeats.add(ShowSeat.builder()
                    .show(savedShow)
                    .seat(seat)
                    .category(seat.getCategory())
                    .price(price)
                    .status(SeatStatus.AVAILABLE)
                    .build());
        }
        showSeatRepository.saveAll(showSeats);

        log.info("Created show {} for event {} with {} seats", savedShow.getId(), eventId, showSeats.size());
        return toShowDto(savedShow, savedPrices);
    }

    @Override
    @Transactional(readOnly = true)
    public EventReportDto getEventReport(Long eventId) {
        Event event = getEventOrThrow(eventId);
        verifyOwner(event);

        return EventReportDto.builder()
                .eventId(event.getId())
                .title(event.getTitle())
                .confirmedBookings(bookingRepository.countConfirmedForEvent(eventId))
                .cancelledBookings(bookingRepository.countCancelledForEvent(eventId))
                .totalRevenue(bookingRepository.totalRevenueForEvent(eventId))
                .build();
    }

    private Event getEventOrThrow(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
    }

    private void verifyOwner(Event event) {
        AuthenticatedUser currentUser = currentUser();
        if (!currentUser.getId().equals(event.getOrganiser().getId())) {
            throw new AccessDeniedException("You are not authorized to manage this event");
        }
    }

    private AuthenticatedUser currentUser() {
        return securityHelper.getCurrentAuthenticatedUser()
                .orElseThrow(() -> new AccessDeniedException("Cannot identify the authenticated user"));
    }

    private ShowResponseDto toShowDto(Show show) {
        List<ShowCategoryPrice> prices = showCategoryPriceRepository.findByShow(show);
        return toShowDto(show, prices);
    }

    private ShowResponseDto toShowDto(Show show, List<ShowCategoryPrice> prices) {
        List<CategoryPriceDto> priceDtos = prices.stream()
                .map(p -> CategoryPriceDto.builder()
                        .categoryName(p.getCategory().getName())
                        .price(p.getPrice())
                        .build())
                .toList();

        return ShowResponseDto.builder()
                .id(show.getId())
                .eventId(show.getEvent().getId())
                .eventTitle(show.getEvent().getTitle())
                .venueId(show.getVenue().getId())
                .venueName(show.getVenue().getName())
                .showDate(show.getShowDate())
                .showTime(show.getShowTime())
                .status(show.getStatus().name())
                .categoryPrices(priceDtos)
                .build();
    }

    private EventResponseDto toDto(Event event, List<ShowResponseDto> shows) {
        return EventResponseDto.builder()
                .id(event.getId())
                .organiserId(event.getOrganiser().getId())
                .title(event.getTitle())
                .type(event.getType().name())
                .description(event.getDescription())
                .active(event.isActive())
                .shows(shows)
                .build();
    }
}
