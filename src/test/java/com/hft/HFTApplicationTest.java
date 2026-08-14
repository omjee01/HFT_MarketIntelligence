package com.hft;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class HFTApplicationTest {

    @Test
    void contextLoads() {
        // Verifies all Spring beans wire correctly
    }
}