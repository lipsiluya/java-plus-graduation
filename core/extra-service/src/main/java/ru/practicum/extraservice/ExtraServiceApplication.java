package ru.practicum.extraservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class ExtraServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExtraServiceApplication.class, args);
    }
}
