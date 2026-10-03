package com.radiotech.radiotech_backend.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

@Service
public class QrCodeService {
    public String toDataUri(String value) throws Exception {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Valore QR obbligatorio.");
        }
        BitMatrix matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 420, 420);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        }
    }
}
