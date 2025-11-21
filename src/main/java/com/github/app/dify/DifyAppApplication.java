package com.github.app.dify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DifyAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(DifyAppApplication.class, args);
    }

}
