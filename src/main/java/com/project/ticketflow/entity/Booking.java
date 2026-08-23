package com.project.ticketflow.entity;

import com.project.ticketflow.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(
        indexes = {
                // for the hold-expiry cleanup cron
                @Index(name = "idx_booking_status_expiry", columnList = "status, holdExpiresAt"),
                @Index(name = "idx_booking_customer_status", columnList = "customer_id, status")
        }
)
public class Booking {

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

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    @Column(unique = true)
    private String bookingReference;

    private String customerName;

    private String customerEmail;

    private String customerPhone;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    // deadline while status = HELD; null once CONFIRMED/CANCELLED/EXPIRED
    private LocalDateTime holdExpiresAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
    @ToString.Exclude
    private Set<BookingSeat> seats;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;
}
