package com.project.ticketflow.repository;

import com.project.ticketflow.entity.SeatCategory;
import com.project.ticketflow.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeatCategoryRepository extends JpaRepository<SeatCategory, Long> {
    List<SeatCategory> findByVenue(Venue venue);

    Optional<SeatCategory> findByVenueAndName(Venue venue, String name);
}
