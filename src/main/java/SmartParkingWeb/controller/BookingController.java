
package SmartParkingWeb.controller;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final JdbcTemplate jdbcTemplate;

    public BookingController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping
    @Transactional
    public Map<String, Object> createBooking(
            @RequestBody Map<String, Object> request) {

        String customerName = String.valueOf(
            request.getOrDefault("customerName", "")
        ).trim();

        String vehicleNumber = String.valueOf(
            request.getOrDefault("vehicleNumber", "")
        ).trim();

        String slotNumber = String.valueOf(
            request.getOrDefault("slotNumber", "")
        ).trim();

        int locationId;
        int duration;

        try {
            locationId = Integer.parseInt(
                String.valueOf(request.get("locationId"))
            );

            duration = Integer.parseInt(
                String.valueOf(request.get("durationHours"))
            );
        } catch (Exception e) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid location or duration"
            );
        }

        if (customerName.isEmpty()
                || vehicleNumber.isEmpty()
                || slotNumber.isEmpty()
                || duration < 1
                || duration > 24) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Please enter valid booking details"
            );
        }

        Map<String, Object> slot;

        try {
            slot = jdbcTemplate.queryForMap(
                """
                SELECT p.slot_number, l.hourly_rate
                FROM parking_slots p
                JOIN parking_locations l
                  ON p.location_id = l.location_id
                WHERE p.slot_number = ?
                  AND p.location_id = ?
                  AND p.status = 'AVAILABLE'
                  AND l.active = TRUE
                FOR UPDATE
                """,
                slotNumber, locationId
            );
        } catch (Exception e) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "This parking slot is not available. Please refresh."
            );
        }

        BigDecimal hourlyRate =
            new BigDecimal(slot.get("hourly_rate").toString());

        BigDecimal totalAmount =
            hourlyRate.multiply(BigDecimal.valueOf(duration));

        jdbcTemplate.update(
            """
            INSERT INTO bookings
            (customer_name, vehicle_number, slot_number,
             duration_hours, total_amount, status)
            VALUES (?, ?, ?, ?, ?, 'BOOKED')
            """,
            customerName,
            vehicleNumber,
            slotNumber,
            duration,
            totalAmount
        );

        jdbcTemplate.update(
            """
            UPDATE parking_slots
            SET status = 'OCCUPIED'
            WHERE slot_number = ?
            """,
            slotNumber
        );

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Parking booked successfully!");
        result.put("customerName", customerName);
        result.put("vehicleNumber", vehicleNumber);
        result.put("slotNumber", slotNumber);
        result.put("durationHours", duration);
        result.put("totalAmount", totalAmount);
        result.put("status", "BOOKED");

        return result;
    }
@GetMapping("/slots")
public java.util.List<Map<String, Object>> getAvailableSlots(
        @RequestParam int locationId) {

    String sql = """
        SELECT slot_id, slot_number
        FROM parking_slots
        WHERE location_id = ?
          AND status = 'AVAILABLE'
        ORDER BY slot_number
        """;

    return jdbcTemplate.queryForList(sql, locationId);
}
@GetMapping("/history")
public java.util.List<Map<String, Object>> getBookingHistory() {

    String sql = """
        SELECT booking_id,
               customer_name,
               vehicle_number,
               slot_number,
               booking_date,
               duration_hours,
               total_amount,
               status
        FROM bookings
        ORDER BY booking_id DESC
        """;

    return jdbcTemplate.queryForList(sql);
}
@PostMapping("/{bookingId}/cancel")
@Transactional
public Map<String, Object> cancelBooking(
        @PathVariable int bookingId) {

    String selectSql = """
        SELECT slot_number, status
        FROM bookings
        WHERE booking_id = ?
        FOR UPDATE
        """;

    List<Map<String, Object>> rows =
            jdbcTemplate.queryForList(selectSql, bookingId);

    if (rows.isEmpty()) {
        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Booking not found"
        );
    }

    String slotNumber =
            rows.get(0).get("slot_number").toString();

    String status =
            rows.get(0).get("status").toString();

    if (!"BOOKED".equalsIgnoreCase(status)) {
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Booking is already cancelled"
        );
    }

    String updateBooking = """
        UPDATE bookings
        SET status = 'CANCELLED'
        WHERE booking_id = ?
        """;

    jdbcTemplate.update(updateBooking, bookingId);

    String updateSlot = """
        UPDATE parking_slots
        SET status = 'AVAILABLE'
        WHERE slot_number = ?
        """;

    jdbcTemplate.update(updateSlot, slotNumber);

    return Map.of(
        "message", "Booking cancelled successfully!",
        "bookingId", bookingId,
        "slotNumber", slotNumber,
        "status", "CANCELLED"
    );
}
}
