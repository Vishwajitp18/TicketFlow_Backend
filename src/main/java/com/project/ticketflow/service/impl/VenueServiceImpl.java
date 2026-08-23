package com.project.ticketflow.service.impl;

import com.project.ticketflow.dto.venue.*;
import com.project.ticketflow.entity.Seat;
import com.project.ticketflow.entity.SeatCategory;
import com.project.ticketflow.entity.Venue;
import com.project.ticketflow.exception.BadRequestException;
import com.project.ticketflow.exception.ResourceNotFoundException;
import com.project.ticketflow.repository.SeatCategoryRepository;
import com.project.ticketflow.repository.SeatRepository;
import com.project.ticketflow.repository.VenueRepository;
import com.project.ticketflow.service.VenueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class VenueServiceImpl implements VenueService {

    private final VenueRepository venueRepository;
    private final SeatCategoryRepository seatCategoryRepository;
    private final SeatRepository seatRepository;

    @Override
    @Transactional
    public VenueResponseDto createVenue(VenueRequestDto requestDto) {
        log.info("Creating venue: {}", requestDto.getName());
        Venue venue = Venue.builder()
                .name(requestDto.getName())
                .address(requestDto.getAddress())
                .city(requestDto.getCity())
                .active(true)
                .build();
        Venue saved = venueRepository.save(venue);
        return toDto(saved);
    }

    @Override
    public VenueResponseDto getVenue(Long venueId) {
        Venue venue = getVenueOrThrow(venueId);
        return toDto(venue);
    }

    @Override
    public List<VenueResponseDto> getAllVenues() {
        return venueRepository.findAll().stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public List<SeatDto> addSeats(Long venueId, BulkSeatRequestDto requestDto) {
        Venue venue = getVenueOrThrow(venueId);
        log.info("Adding {} seat row(s) to venue {}", requestDto.getRows().size(), venueId);

        Map<String, SeatCategory> categoryCache = new HashMap<>();
        List<Seat> seatsToSave = new ArrayList<>();

        for (BulkSeatRowDto row : requestDto.getRows()) {
            SeatCategory category = categoryCache.computeIfAbsent(row.getCategoryName(), name ->
                    seatCategoryRepository.findByVenueAndName(venue, name)
                            .orElseGet(() -> seatCategoryRepository.save(
                                    SeatCategory.builder().venue(venue).name(name).build())));

            for (int seatNumber = 1; seatNumber <= row.getSeatCount(); seatNumber++) {
                seatsToSave.add(Seat.builder()
                        .venue(venue)
                        .category(category)
                        .rowLabel(row.getRowLabel())
                        .seatNumber(seatNumber)
                        .label(row.getRowLabel() + seatNumber)
                        .build());
            }
        }

        List<Seat> saved;
        try {
            saved = seatRepository.saveAll(seatsToSave);
        } catch (Exception e) {
            throw new BadRequestException("Could not add seats — a row/seat-number combination may already exist for this venue");
        }

        return saved.stream().map(this::toSeatDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeatDto> getSeats(Long venueId) {
        Venue venue = getVenueOrThrow(venueId);
        return seatRepository.findByVenue(venue).stream().map(this::toSeatDto).toList();
    }

    private Venue getVenueOrThrow(Long venueId) {
        return venueRepository.findById(venueId)
                .orElseThrow(() -> new ResourceNotFoundException("Venue not found with id: " + venueId));
    }

    private VenueResponseDto toDto(Venue venue) {
        return VenueResponseDto.builder()
                .id(venue.getId())
                .name(venue.getName())
                .address(venue.getAddress())
                .city(venue.getCity())
                .active(venue.isActive())
                .build();
    }

    private SeatDto toSeatDto(Seat seat) {
        return SeatDto.builder()
                .id(seat.getId())
                .rowLabel(seat.getRowLabel())
                .seatNumber(seat.getSeatNumber())
                .label(seat.getLabel())
                .categoryName(seat.getCategory().getName())
                .build();
    }
}
