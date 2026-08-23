package com.project.ticketflow.service.event;

import com.project.ticketflow.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

/**
 * Fires only after the originating transaction commits — uses Spring's in-process event bus
 * so no message broker is needed. @Async keeps email delivery off the request thread.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailEventListener {

    private final EmailService emailService;

    @Async("emailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        try {
            emailService.sendBookingConfirmation(event.bookingId());
        } catch (Exception e) {
            log.error("Failed to send booking confirmation email for booking {} (non-fatal)", event.bookingId(), e);
        }
    }

    @Async("emailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeatOfferCreated(SeatOfferCreatedEvent event) {
        try {
            emailService.sendWaitlistOffer(event.token());
        } catch (Exception e) {
            log.error("Failed to send waitlist offer email for offer group {} (non-fatal)", event.token(), e);
        }
    }
}
