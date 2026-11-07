
package SmartParkingWeb.controller;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ParkingController {

    private final JdbcTemplate jdbcTemplate;

    public ParkingController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/locations")
    public List<Map<String, Object>> getLocations() {

        String sql = """
            SELECT l.location_id,
                   l.facility_name,
                   l.area,
                   l.address,
                   l.hourly_rate,
                   COUNT(p.slot_id) AS total_slots,
                   COALESCE(SUM(
                       CASE WHEN p.status = 'AVAILABLE'
                       THEN 1 ELSE 0 END
                   ), 0) AS available_slots
            FROM parking_locations l
            LEFT JOIN parking_slots p
                ON l.location_id = p.location_id
            WHERE l.active = TRUE
            GROUP BY l.location_id, l.facility_name,
                     l.area, l.address, l.hourly_rate
            ORDER BY l.area
            """;

        return jdbcTemplate.queryForList(sql);
    }

    @GetMapping("/parking/search")
    public List<Map<String, Object>> searchParking(
            @RequestParam(defaultValue = "") String q) {

        String sql = """
            SELECT l.location_id,
                   l.facility_name,
                   l.area,
                   l.address,
                   l.hourly_rate,
                   COUNT(p.slot_id) AS total_slots,
                   COALESCE(SUM(
                       CASE WHEN p.status = 'AVAILABLE'
                       THEN 1 ELSE 0 END
                   ), 0) AS available_slots
            FROM parking_locations l
            LEFT JOIN parking_slots p
                ON l.location_id = p.location_id
            WHERE l.active = TRUE
              AND (
                  LOWER(l.area) LIKE LOWER(?)
                  OR LOWER(l.facility_name) LIKE LOWER(?)
                  OR LOWER(l.address) LIKE LOWER(?)
              )
            GROUP BY l.location_id, l.facility_name,
                     l.area, l.address, l.hourly_rate
            ORDER BY l.area
            """;

        String search = "%" + q.trim() + "%";

        return jdbcTemplate.queryForList(
            sql, search, search, search
        );
    }
}
