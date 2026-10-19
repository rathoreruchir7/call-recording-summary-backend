package dev.callsummary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CallApplication {
    public static void main(String[] args) {
        SpringApplication.run(CallApplication.class, args);
    }
}
