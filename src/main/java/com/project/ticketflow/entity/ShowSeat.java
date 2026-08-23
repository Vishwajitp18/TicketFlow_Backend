package com.project.ticketflow.entity;

import com.project.ticketflow.enums.SeatStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(
        uniqueConstraints = @UniqueConstraint(columnNames = {"show_id", "seat_id"}),
        indexes = {
                @Index(name = "idx_show_seat_status", columnList = "show_id, status"),
                @Index(name = "idx_show_seat_category_status", columnList = "show_id, category_id, status")
        }
)
public class ShowSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    @ToString.Exclude
    private Show show;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    @ToString.Exclude
    private Seat seat;

    // denormalized for fast per-category availability queries without joining Seat
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    @ToString.Exclude
    private SeatCategory category;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SeatStatus status;

    // the booking currently holding/owning this seat (HELD or BOOKED); null when AVAILABLE/OFFERED
    private Long heldByBookingId;

    // deadline for the current HELD or OFFERED state; scanned by the release cron
    private LocalDateTime holdExpiresAt;

    @Version
    @Column(nullable = false)
    private Long version;
}
