package com.personal.rebooked;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class RebookedApplication {

    @GetMapping("/")
    public String home() {
        return "Hello World34374";
    }


    public static void main(String[] args) {
        SpringApplication.run(RebookedApplication.class, args);
    }

}
