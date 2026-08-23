package com.project.ticketflow.repository;

import com.project.ticketflow.entity.Event;
import com.project.ticketflow.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShowRepository extends JpaRepository<Show, Long> {
    List<Show> findByEventOrderByShowDateAscShowTimeAsc(Event event);
}
