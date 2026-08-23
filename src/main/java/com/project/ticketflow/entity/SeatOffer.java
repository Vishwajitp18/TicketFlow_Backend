package com.project.ticketflow.entity;

import com.project.ticketflow.enums.OfferStatus;
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
                // for the offer-expiry cleanup cron
                @Index(name = "idx_seat_offer_status_expiry", columnList = "status, expiresAt"),
                // a multi-seat offer is several rows sharing one token (one row per seat) —
                // this is how the group is looked back up, e.g. on accept
                @Index(name = "idx_seat_offer_token", columnList = "token")
        }
)
public class SeatOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "waitlist_entry_id", nullable = false)
    @ToString.Exclude
    private WaitlistEntry waitlistEntry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_seat_id", nullable = false)
    @ToString.Exclude
    private ShowSeat showSeat;

    // NOT unique — every seat in one multi-seat offer shares the same token
    @Column(nullable = false)
    private String token;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OfferStatus status;

    @Version
    @Column(nullable = false)
    private Long version;
}
