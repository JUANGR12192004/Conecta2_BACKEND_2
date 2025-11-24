package com.example.worker_registry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class WorkerRegistryApplication {
    public static void main(String[] args) {
        // Usa zona horaria fija UTC-5 (America/Bogota) para toda la app
        TimeZone.setDefault(TimeZone.getTimeZone("America/Bogota"));
        SpringApplication.run(WorkerRegistryApplication.class, args);
    }
}
