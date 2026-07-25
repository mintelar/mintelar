package com.hilo.rewards;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HiloRewardsApplication {

    public static void main(String[] args) {
        SpringApplication.run(HiloRewardsApplication.class, args);
    }
}
