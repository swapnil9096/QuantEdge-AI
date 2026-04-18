package com.trading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MultibaggerSwingTradingApplication {
    public static void main(String[] args) {
        SpringApplication.run(MultibaggerSwingTradingApplication.class, args);
    }
}
