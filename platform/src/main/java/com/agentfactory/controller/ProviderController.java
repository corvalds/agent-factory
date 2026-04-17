package com.agentfactory.controller;

import com.agentfactory.model.Provider;
import com.agentfactory.service.ProviderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private final ProviderService providerService;

    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @GetMapping
    public List<Provider> list(@RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        if (activeOnly) {
            return providerService.findActive();
        }
        return providerService.findAll();
    }

    @GetMapping("/{id}")
    public Provider get(@PathVariable Long id) {
        return providerService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Provider create(@RequestBody Provider provider) {
        return providerService.create(provider);
    }

    @PutMapping("/{id}")
    public Provider update(@PathVariable Long id, @RequestBody Provider provider) {
        return providerService.update(id, provider);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        providerService.delete(id);
    }

    @PostMapping("/{id}/test")
    public TestResult testConnection(@PathVariable Long id) {
        try {
            providerService.decryptApiKey(id);
            return new TestResult(true, "Connection successful");
        } catch (Exception e) {
            return new TestResult(false, e.getMessage());
        }
    }

    record TestResult(boolean success, String message) {}
}
