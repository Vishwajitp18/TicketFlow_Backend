package com.project.ticketflow.repository;

import com.project.ticketflow.entity.Seat;
import com.project.ticketflow.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByVenue(Venue venue);

    long countByVenue(Venue venue);
}
