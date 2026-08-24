package com.project.ticketflow.service.event;

import com.project.ticketflow.dto.booking.SeatStatusUpdateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Replaces polling GET /shows/{id}/seatmap with a live push: whenever a seat's status
 * changes, one delta is broadcast to everyone currently subscribed to that show's topic.
 * AFTER_COMMIT is essential here, not just tidy — broadcasting before commit could tell a
 * client a seat is AVAILABLE when the transaction that freed it might still roll back
 * (e.g. a pessimistic lock timeout later in the same transaction), advertising a seat that
 * was never actually released.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeatMapEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @Async("emailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSeatStatusChanged(SeatStatusChangedEvent event) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/shows/" + event.showId() + "/seatmap",
                    SeatStatusUpdateDto.builder()
                            .showSeatId(event.showSeatId())
                            .status(event.status())
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to broadcast seat status update for show {} seat {} (non-fatal)",
                    event.showId(), event.showSeatId(), e);
        }
    }
}
