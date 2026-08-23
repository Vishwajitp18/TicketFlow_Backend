package com.project.ticketflow.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

@Component
public class QrCodeGenerator {

    private static final int SIZE = 300;

    /**
     * Encodes the given content into a QR PNG's raw bytes. Use this for an email attachment
     * (CID-embedded — see EmailServiceImpl) since webmail clients like Gmail strip inline
     * "data:" URIs but do render true CID-attached images.
     */
    public byte[] generatePng(String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, SIZE, SIZE);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (WriterException | IOException e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    /**
     * Base64 form for a "data:image/png;base64,..." URI — fine for a browser/app JSON
     * response (BookingResponseDto), just not for email (see generatePng above).
     */
    public String generateBase64Png(String content) {
        return Base64.getEncoder().encodeToString(generatePng(content));
    }
}
