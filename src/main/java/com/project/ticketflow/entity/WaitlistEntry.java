package com.project.ticketflow.entity;

import com.project.ticketflow.enums.WaitlistStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(
        indexes = {
                // for FIFO lookup of the next WAITING entry per (show, category)
                @Index(name = "idx_waitlist_show_category_status_joined",
                        columnList = "show_id, category_id, status, joinedAt")
        }
)
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    @ToString.Exclude
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    @ToString.Exclude
    private Show show;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    @ToString.Exclude
    private SeatCategory category;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private WaitlistStatus status;

    // how many seats together this entry is waiting for — fulfillment is all-or-nothing,
    // see SeatReleaseService
    @Column(nullable = false)
    private Integer requestedQuantity;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    @Version
    @Column(nullable = false)
    private Long version;
}
