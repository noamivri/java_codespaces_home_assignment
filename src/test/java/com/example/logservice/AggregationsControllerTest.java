
package com.example.logservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AggregationsControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LogRecordRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        
        // Create sample data
        Instant baseTime = Instant.parse("2025-07-01T12:00:00Z");
        
        // Add logs for app1 (most logs)
        for (int i = 0; i < 10; i++) {
            LogRecord log = new LogRecord();
            log.setApp("app1");
            log.setLevel(i < 3 ? "ERROR" : "INFO");
            log.setTs(baseTime.plusSeconds(i * 60));
            log.setMessage("Test message " + i);
            repository.save(log);
        }
        
        // Add logs for app2
        for (int i = 0; i < 5; i++) {
            LogRecord log = new LogRecord();
            log.setApp("app2");
            log.setLevel(i < 2 ? "ERROR" : "INFO");
            log.setTs(baseTime.plusSeconds(i * 60));
            log.setMessage("Test message " + i);
            repository.save(log);
        }
        
        // Add logs for app3
        for (int i = 0; i < 3; i++) {
            LogRecord log = new LogRecord();
            log.setApp("app3");
            log.setLevel("INFO");
            log.setTs(baseTime.plusSeconds(i * 60));
            log.setMessage("Test message " + i);
            repository.save(log);
        }
    }

    @Test
    void topAppsReturnsTopKApps() throws Exception {
        mockMvc.perform(get("/aggregations/top-apps")
                .param("k", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].app", is("app1")))
                .andExpect(jsonPath("$[0].count", is(10)))
                .andExpect(jsonPath("$[1].app", is("app2")))
                .andExpect(jsonPath("$[1].count", is(5)))
                .andExpect(jsonPath("$[2].app", is("app3")))
                .andExpect(jsonPath("$[2].count", is(3)));
    }

    @Test
    void topAppsWithDefaultKReturnsTop5() throws Exception {
        mockMvc.perform(get("/aggregations/top-apps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3))); // We only have 3 apps
    }

    @Test
    void topAppsWithTimeRangeFilters() throws Exception {
        // Filter to only include logs from first 3 minutes
        mockMvc.perform(get("/aggregations/top-apps")
                .param("from", "2025-07-01T12:00:00Z")
                .param("to", "2025-07-01T12:03:00Z")
                .param("k", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void errorRateReturnsErrorCountsByWindow() throws Exception {
        // Test with 5 minute window (default)
        mockMvc.perform(get("/aggregations/error-rate")
                .param("app", "app1")
                .param("window", "5m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))) // All errors in same 5-min bucket
                .andExpect(jsonPath("$[0].error_count", is(3)));
    }

    @Test
    void errorRateWithOneMinuteWindow() throws Exception {
        mockMvc.perform(get("/aggregations/error-rate")
                .param("app", "app1")
                .param("window", "1m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3))) // 3 errors in separate 1-min buckets
                .andExpect(jsonPath("$[0].error_count", is(1)))
                .andExpect(jsonPath("$[1].error_count", is(1)))
                .andExpect(jsonPath("$[2].error_count", is(1)));
    }

    @Test
    void errorRateForAppWithNoErrors() throws Exception {
        mockMvc.perform(get("/aggregations/error-rate")
                .param("app", "app3"))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));
    }

    @Test
    void errorRateRequiresAppParameter() throws Exception {
        mockMvc.perform(get("/aggregations/error-rate"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void errorRateWithInvalidWindowFormat() throws Exception {
        mockMvc.perform(get("/aggregations/error-rate")
                .param("app", "app1")
                .param("window", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Invalid window format")));
    }

    @Test
    void errorRateWithEmptyWindowFormat() throws Exception {
        mockMvc.perform(get("/aggregations/error-rate")
                .param("app", "app1")
                .param("window", "m"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Invalid window format")));
    }
}
