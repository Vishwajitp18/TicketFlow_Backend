package com.project.ticketflow.repository;

import com.project.ticketflow.entity.Event;
import com.project.ticketflow.enums.EventType;
import com.project.ticketflow.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;

public interface EventRepository extends JpaRepository<Event, Long> {

    Page<Event> findByOrganiser(User organiser, Pageable pageable);

    // fuzzy on title (pg_trgm similarity — typo-tolerant, e.g. "avngers" still matches
    // "Avengers"), exact on type/city. EXISTS instead of a join keeps this dedup-free so an
    // ORDER BY on the similarity score is legal even with a plain (non-DISTINCT) select.
    // cast(:query as string) is required — without it Hibernate can't infer a type for the
    // generic function('similarity', ...) parameter and (on at least one of its two bound
    // occurrences) sends it to Postgres as bytea, which similarity() rejects outright.
    // The EXISTS also always requires at least one upcoming show — an event whose every show
    // has already happened has nothing bookable left, so it shouldn't surface in search.
    @Query("""
            select e from Event e
            where e.active = true
            and (:type is null or e.type = :type)
            and exists (
                select 1 from Show s
                where s.event = e
                and (s.showDate > :today or (s.showDate = :today and s.showTime >= :nowTime))
                and (:city is null or s.venue.city = :city)
            )
            and (:query is null or function('similarity', e.title, cast(:query as string)) > 0.2)
            order by case when :query is null then 0 else function('similarity', e.title, cast(:query as string)) end desc
            """)
    Page<Event> searchEvents(@Param("type") EventType type, @Param("city") String city,
                              @Param("query") String query, @Param("today") LocalDate today,
                              @Param("nowTime") LocalTime nowTime, Pageable pageable);
}
