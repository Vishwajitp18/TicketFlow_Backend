package com.project.ticketflow.service.impl;

import brevo.ApiException;
import brevoApi.TransactionalEmailsApi;
import brevoModel.SendSmtpEmail;
import brevoModel.SendSmtpEmailAttachment;
import brevoModel.SendSmtpEmailSender;
import brevoModel.SendSmtpEmailTo;
import com.project.ticketflow.entity.Booking;
import com.project.ticketflow.entity.SeatOffer;
import com.project.ticketflow.exception.ResourceNotFoundException;
import com.project.ticketflow.repository.BookingRepository;
import com.project.ticketflow.repository.SeatOfferRepository;
import com.project.ticketflow.service.EmailService;
import com.project.ticketflow.util.QrCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final TemplateEngine templateEngine;
    private final TransactionalEmailsApi transactionalEmailsApi;
    private final QrCodeGenerator qrCodeGenerator;
    private final BookingRepository bookingRepository;
    private final SeatOfferRepository seatOfferRepository;

    @Value("${ticketflow.email.from.email}")
    private String fromEmail;

    @Value("${ticketflow.email.from.name}")
    private String fromName;

    @Value("${ticketflow.app.base-url}")
    private String appBaseUrl;

    @Override
    @Transactional(readOnly = true)
    public void sendBookingConfirmation(Long bookingId) {
        Booking booking = bookingRepository.findByIdWithSeats(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        // Gmail and most webmail clients strip inline "data:" image URIs outright — they only
        // render a QR that's a real CID-attached image, referenced in the template as
        // "cid:<name>" (see SendSmtpEmailAttachment#name below and booking_confirmed.html).
        String qrFileName = "qrcode.png";
        byte[] qrPng = qrCodeGenerator.generatePng(booking.getBookingReference());
        SendSmtpEmailAttachment qrAttachment = new SendSmtpEmailAttachment().content(qrPng).name(qrFileName);

        List<String> seatLabels = booking.getSeats().stream()
                .map(bs -> bs.getShowSeat().getSeat().getLabel())
                .sorted()
                .toList();

        String html = renderTemplate("booking_confirmed", Map.of(
                "customerName", booking.getCustomerName(),
                "eventTitle", booking.getShow().getEvent().getTitle(),
                "showDate", booking.getShow().getShowDate().toString(),
                "showTime", booking.getShow().getShowTime().toString(),
                "venueName", booking.getShow().getVenue().getName(),
                "seatLabels", String.join(", ", seatLabels),
                "amount", booking.getAmount().toString(),
                "bookingReference", booking.getBookingReference(),
                "qrCid", "cid:" + qrFileName
        ));

        send(booking.getCustomerEmail(), "Your ticket for " + booking.getShow().getEvent().getTitle(), html,
                List.of(qrAttachment));
        log.info("Sent booking confirmation email for booking {}", bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    public void sendWaitlistOffer(String token) {
        List<SeatOffer> offers = seatOfferRepository.findAllByToken(token);
        if (offers.isEmpty()) {
            throw new ResourceNotFoundException("Seat offer group not found for token: " + token);
        }
        // every row in the group shares the same waitlist entry / show / category / expiry —
        // only the seat differs
        SeatOffer first = offers.get(0);
        int seatCount = offers.size();

        String acceptLink = appBaseUrl + "/waitlist/offers/" + token + "/accept";

        String html = renderTemplate("waitlist_offer", Map.of(
                "customerName", first.getWaitlistEntry().getCustomer().getName(),
                "eventTitle", first.getShowSeat().getShow().getEvent().getTitle(),
                "categoryName", first.getShowSeat().getCategory().getName(),
                "seatCount", seatCount,
                "expiresAt", first.getExpiresAt().toString(),
                "acceptLink", acceptLink
        ));

        send(first.getWaitlistEntry().getCustomer().getEmail(),
                seatCount + " seat(s) available for " + first.getShowSeat().getShow().getEvent().getTitle(),
                html, List.of());
        log.info("Sent waitlist offer email for offer group {} ({} seats)", token, seatCount);
    }

    private String renderTemplate(String templateName, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        return templateEngine.process(templateName, context);
    }

    private void send(String to, String subject, String htmlContent, List<SendSmtpEmailAttachment> attachments) {
        try {
            SendSmtpEmail email = new SendSmtpEmail();
            email.setSender(new SendSmtpEmailSender().email(fromEmail).name(fromName));
            email.setTo(Collections.singletonList(new SendSmtpEmailTo().email(to)));
            email.setSubject(subject);
            email.setHtmlContent(htmlContent);
            if (!attachments.isEmpty()) {
                email.setAttachment(attachments);
            }
            transactionalEmailsApi.sendTransacEmail(email);
        } catch (ApiException e) {
            log.error("Brevo API error sending email to {}", to, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
