package com.project.ticketflow.repository;

import com.project.ticketflow.entity.SeatOffer;
import com.project.ticketflow.enums.OfferStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SeatOfferRepository extends JpaRepository<SeatOffer, Long> {

    // a multi-seat offer is several rows sharing one token, one per seat — order by id so
    // both the API layer and the email always see the group in a stable order
    @Query("""
            select o from SeatOffer o join fetch o.waitlistEntry join fetch o.showSeat
            where o.token = :token order by o.id asc
            """)
    List<SeatOffer> findAllByToken(@Param("token") String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SeatOffer o where o.token = :token order by o.id asc")
    List<SeatOffer> findAllByTokenForUpdate(@Param("token") String token);

    List<SeatOffer> findByStatusAndExpiresAtBefore(OfferStatus status, LocalDateTime threshold, Pageable pageable);
}
