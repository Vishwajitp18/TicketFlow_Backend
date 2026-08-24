package com.project.ticketflow.repository;

import com.project.ticketflow.entity.Event;
import com.project.ticketflow.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ShowRepository extends JpaRepository<Show, Long> {
    List<Show> findByEventOrderByShowDateAscShowTimeAsc(Event event);

    // showDate/showTime are split columns, so "upcoming" needs the usual two-part comparison
    // rather than a single timestamp comparison
    @Query("""
            select s from Show s
            where s.event = :event
            and (s.showDate > :today or (s.showDate = :today and s.showTime >= :nowTime))
            order by s.showDate asc, s.showTime asc
            """)
    List<Show> findUpcomingByEvent(@Param("event") Event event, @Param("today") LocalDate today,
                                    @Param("nowTime") LocalTime nowTime);
}
