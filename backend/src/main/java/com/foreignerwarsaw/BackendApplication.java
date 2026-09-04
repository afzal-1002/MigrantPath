package com.foreignerwarsaw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling: Phase 12's TokenCleanupService (brief §34/§35) - a single-instance
// in-process scheduler, deliberately no distributed scheduling infrastructure (brief §35's own
// "single-instance scheduler is acceptable for current deployment model" - see that class's own
// Javadoc for the horizontal-scaling implication this carries).
@EnableScheduling
@SpringBootApplication
public class BackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(BackendApplication.class, args);
  }
}
