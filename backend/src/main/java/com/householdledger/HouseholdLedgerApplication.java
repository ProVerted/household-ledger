package com.householdledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HouseholdLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(HouseholdLedgerApplication.class, args);
    }
}
