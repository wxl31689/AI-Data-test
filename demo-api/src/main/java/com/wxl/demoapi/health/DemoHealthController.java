package com.wxl.demoapi.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/demo/health")
public class DemoHealthController {

    @GetMapping
    public DemoHealthResponse health() {
        return new DemoHealthResponse("UP", "vehicle-demo-api", Instant.now());
    }
}

