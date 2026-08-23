package com.project.ticketflow.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BookingResponseDto {
    private Long id;
    private Long showId;
    private String eventTitle;
    private String status;
    private String bookingReference;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private BigDecimal amount;
    private LocalDateTime holdExpiresAt;
    // "data:image/png;base64,<qrCodeBase64>" — data URIs render fine in a browser/app,
    // unlike email (see EmailServiceImpl, which uses a CID attachment instead). Only
    // populated once the booking is CONFIRMED (i.e. bookingReference is set).
    private String qrCodeBase64;
    private List<BookingSeatDto> seats;
}
