package edu.bits.itsm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Entry point of the I-ITSM backend. */
@SpringBootApplication
@EnableScheduling
public class ItsmApplication {
    public static void main(String[] args) {
        SpringApplication.run(ItsmApplication.class, args);
    }
}
