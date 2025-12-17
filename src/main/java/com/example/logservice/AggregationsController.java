
package com.example.logservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class AggregationsController {
    private final LogRecordRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();

    public AggregationsController(LogRecordRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/aggregations/top-apps")
    public ResponseEntity<String> topApps(
            @RequestParam(required=false) String from,
            @RequestParam(required=false) String to,
            @RequestParam(required=false, defaultValue="5") int k) {
        try {
            Instant fromTime = from != null && !from.isEmpty() ? Instant.parse(from) : null;
            Instant toTime = to != null && !to.isEmpty() ? Instant.parse(to) : null;
            
            List<Object[]> results = repository.findTopAppsByCount(fromTime, toTime);
            
            ArrayNode jsonArray = mapper.createArrayNode();
            int count = 0;
            for (Object[] row : results) {
                if (count >= k) break;
                ObjectNode item = mapper.createObjectNode();
                item.put("app", (String) row[0]);
                item.put("count", ((Number) row[1]).longValue());
                jsonArray.add(item);
                count++;
            }
            
            return ResponseEntity.ok(mapper.writeValueAsString(jsonArray));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error processing request: " + e.getMessage());
        }
    }

    @GetMapping("/aggregations/error-rate")
    public ResponseEntity<String> errorRate(
            @RequestParam String app,
            @RequestParam(required=false, defaultValue="5m") String window) {
        try {
            // Validate app parameter
            if (app == null || app.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("App parameter is required");
            }
            if (app.length() > 100) {
                return ResponseEntity.badRequest().body("App parameter is too long");
            }
            
            // Parse window duration (e.g., "5m" -> 5 minutes)
            long windowSeconds = parseWindowDuration(window);
            
            List<LogRecord> errorLogs = repository.findErrorLogsByApp(app);
            
            if (errorLogs.isEmpty()) {
                return ResponseEntity.ok("[]");
            }
            
            // Group logs by time buckets
            Map<Instant, Long> buckets = new TreeMap<>();
            for (LogRecord log : errorLogs) {
                if (log.getTs() != null) {
                    // Truncate to bucket start time
                    Instant bucketStart = truncateToWindow(log.getTs(), windowSeconds);
                    buckets.merge(bucketStart, 1L, Long::sum);
                }
            }
            
            // Convert to JSON
            ArrayNode jsonArray = mapper.createArrayNode();
            for (Map.Entry<Instant, Long> entry : buckets.entrySet()) {
                ObjectNode item = mapper.createObjectNode();
                item.put("window_start", entry.getKey().toString());
                item.put("error_count", entry.getValue());
                jsonArray.add(item);
            }
            
            return ResponseEntity.ok(mapper.writeValueAsString(jsonArray));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error processing request: " + e.getMessage());
        }
    }
    
    private long parseWindowDuration(String window) {
        // Parse duration strings like "5m", "60s", "1h"
        if (window == null || window.isEmpty()) {
            throw new IllegalArgumentException("Window duration cannot be empty");
        }
        
        try {
            if (window.endsWith("s")) {
                String numPart = window.substring(0, window.length() - 1);
                if (numPart.isEmpty()) {
                    throw new IllegalArgumentException("Invalid window format: " + window);
                }
                return Long.parseLong(numPart);
            } else if (window.endsWith("m")) {
                String numPart = window.substring(0, window.length() - 1);
                if (numPart.isEmpty()) {
                    throw new IllegalArgumentException("Invalid window format: " + window);
                }
                return Long.parseLong(numPart) * 60;
            } else if (window.endsWith("h")) {
                String numPart = window.substring(0, window.length() - 1);
                if (numPart.isEmpty()) {
                    throw new IllegalArgumentException("Invalid window format: " + window);
                }
                return Long.parseLong(numPart) * 3600;
            }
            // Try parsing as plain seconds
            return Long.parseLong(window);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid window format: " + window + ". Expected format: number followed by s/m/h (e.g., 5m, 60s, 1h)");
        }
    }
    
    private Instant truncateToWindow(Instant timestamp, long windowSeconds) {
        long epochSeconds = timestamp.getEpochSecond();
        long bucketStart = (epochSeconds / windowSeconds) * windowSeconds;
        return Instant.ofEpochSecond(bucketStart);
    }
}
