package com.project.ticketflow.repository;

import com.project.ticketflow.entity.Booking;
import com.project.ticketflow.entity.BookingSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {
    List<BookingSeat> findByBooking(Booking booking);
}
