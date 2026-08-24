package com.project.ticketflow;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class TicketFlowApplication {

    public static void main(String[] args) {
        // Every LocalDateTime.now() in this codebase (booking holdExpiresAt, session
        // lastUsedAt, waitlist joinedAt, seat offer expiresAt, ...) resolves "now" using the
        // JVM's default timezone, which otherwise silently follows whatever the host OS/
        // container is set to — different between a dev machine and wherever this is
        // deployed. Pinning it to UTC here, before Spring (or anything else) initializes,
        // makes every "now" in the app unambiguous and identical across environments. The
        // API always returns UTC timestamps; converting to a viewer's local time is a
        // frontend concern.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(TicketFlowApplication.class, args);
    }

}
