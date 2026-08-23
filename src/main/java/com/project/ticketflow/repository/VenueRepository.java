package com.project.ticketflow.repository;

import com.project.ticketflow.entity.Venue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueRepository extends JpaRepository<Venue, Long> {
    Page<Venue> findByActiveTrue(Pageable pageable);
}
