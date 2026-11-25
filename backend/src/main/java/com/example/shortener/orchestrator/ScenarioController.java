package com.example.shortener.orchestrator;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {
    @GetMapping
    public List<ScenarioPresets.Preset> list() {
        return ScenarioPresets.all();
    }
}
