package com.radiotech.radiotech_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.firebase.enabled=false")
class RadiotechBackendApplicationTests {

	@Test
	void contextLoads() {
	}
}
