package com.vishva007.BookManagement.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.net.InetAddress;

@RestController
public class HealthController {

    @GetMapping("/health")
    public String health() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            return "OK from " + hostname;
        } catch (Exception e) {
            return "OK (hostname unknown)";
        }
    }
}