package com.cityride.service;

import com.cityride.model.Ride;
import com.cityride.model.RideStatus;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class ReceiptService {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);
    private final RideService rides;
    private final AuthService auth;
    private final ObjectProvider<JavaMailSender> mailSenders;
    private final String fromAddress;
    private final String smtpHost;

    public ReceiptService(RideService rides, AuthService auth, ObjectProvider<JavaMailSender> mailSenders,
            @Value("${app.mail.from:}") String fromAddress,
            @Value("${SMTP_HOST:}") String smtpHost) {
        this.rides = rides;
        this.auth = auth;
        this.mailSenders = mailSenders;
        this.fromAddress = fromAddress;
        this.smtpHost = smtpHost;
    }

    public byte[] pdf(String email, Long rideId) {
        Ride ride = requireReceipt(email, rideId);
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 20);
                content.newLineAtOffset(60, 730);
                content.showText("CityRide trip receipt");
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(0, -36);
                for (String line : receiptLines(ride)) {
                    content.showText(ascii(line));
                    content.newLineAtOffset(0, -22);
                }
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not generate the trip receipt.");
        }
    }

    public void email(String email, Long rideId) {
        if (smtpHost.isBlank() || fromAddress.isBlank() || mailSenders.getIfAvailable() == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Email is not configured. Set SMTP_HOST, SMTP_USERNAME, SMTP_PASSWORD, and MAIL_FROM.");
        }
        Ride ride = requireReceipt(email, rideId);
        try {
            var message = mailSenders.getObject().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(ride.getRider().getEmail());
            helper.setSubject("CityRide receipt for trip " + ride.getId());
            helper.setText("Thank you for riding with CityRide. Your PDF receipt is attached.");
            helper.addAttachment("cityride-receipt-" + ride.getId() + ".pdf",
                    new ByteArrayResource(pdf(email, rideId)), "application/pdf");
            mailSenders.getObject().send(message);
        } catch (org.springframework.mail.MailException | jakarta.mail.MessagingException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not send the trip receipt email.");
        }
    }

    private Ride requireReceipt(String email, Long rideId) {
        Ride ride = rides.getAccessible(email, rideId);
        if (!ride.getRider().getEmail().equalsIgnoreCase(email)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the rider can access the payment receipt.");
        }
        if (ride.getStatus() != RideStatus.COMPLETED || !ride.isPaid()) {
            throw new ApiException(HttpStatus.CONFLICT, "A receipt is available after payment is confirmed.");
        }
        return ride;
    }

    private List<String> receiptLines(Ride ride) {
        return List.of(
                "Receipt: CR-" + ride.getId(),
                "Date: " + DATE_FORMAT.format(ride.getCreatedAt()),
                "Rider: " + ride.getRider().getFullName(),
                "From: " + ride.getPickup(),
                "To: " + ride.getDestination(),
                "Distance: " + ride.getDistanceKm() + " km",
                "Promo: " + (ride.getPromoCode() == null ? "None" : ride.getPromoCode()),
                "Discount: INR " + ride.getDiscountAmount(),
                "Driver: " + (ride.getDriver() == null ? "Unassigned" : ride.getDriver().getFullName()),
                "Total paid: INR " + ride.getFare(),
                "Payment status: PAID");
    }

    private String ascii(String value) {
        return value.replaceAll("[^\\x20-\\x7E]", "?").replaceAll("[\\r\\n]", " ");
    }
}
