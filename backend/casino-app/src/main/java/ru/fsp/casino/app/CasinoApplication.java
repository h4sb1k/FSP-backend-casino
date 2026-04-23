package ru.fsp.casino.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "ru.fsp.casino")
@EntityScan(basePackages = "ru.fsp.casino.domain.model")
@EnableJpaRepositories(basePackages = "ru.fsp.casino.domain.repository")
@EnableScheduling
public class CasinoApplication {
    public static void main(String[] args) {
        SpringApplication.run(CasinoApplication.class, args);
    }
}
