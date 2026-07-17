package com.hrniux.underwriting.demo;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hrniux.underwriting.demo.DemoScenarioViews.Detail;
import com.hrniux.underwriting.demo.DemoScenarioViews.Summary;

@RestController
@RequestMapping("/api/v1/demo/scenarios")
public class DemoScenarioController {

    private final DemoScenarioService scenarios;

    public DemoScenarioController(DemoScenarioService scenarios) {
        this.scenarios = scenarios;
    }

    @GetMapping
    public List<Summary> list() {
        return scenarios.list();
    }

    @GetMapping("/{policyNo}")
    public Detail get(@PathVariable String policyNo) {
        return scenarios.get(policyNo);
    }
}
