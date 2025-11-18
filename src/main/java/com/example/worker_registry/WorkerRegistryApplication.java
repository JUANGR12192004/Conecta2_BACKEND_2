package com.example.worker_registry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WorkerRegistryApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkerRegistryApplication.class, args);
    }
}
