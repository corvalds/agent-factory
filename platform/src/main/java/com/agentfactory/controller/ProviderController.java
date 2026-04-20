package com.agentfactory.controller;

import com.agentfactory.model.EventType;
import com.agentfactory.model.Provider;
import com.agentfactory.model.TaskEvent;
import com.agentfactory.repository.TaskEventRepository;
import com.agentfactory.service.ProviderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private final ProviderService providerService;
    private final TaskEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public ProviderController(ProviderService providerService,
                              TaskEventRepository eventRepository,
                              ObjectMapper objectMapper) {
        this.providerService = providerService;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
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

    @GetMapping("/health")
    public List<Map<String, Object>> health() {
        List<Provider> providers = providerService.findAll();
        List<TaskEvent> costEvents = eventRepository.findByEventTypeOrderByTimestampDesc(EventType.COST);
        List<TaskEvent> errorEvents = eventRepository.findByEventTypeOrderByTimestampDesc(EventType.ERROR);

        return providers.stream().map(p -> {
            List<String> models = p.getModels() != null
                ? Arrays.stream(p.getModels().split(",")).map(String::trim).toList()
                : List.of();

            List<TaskEvent> providerCosts = costEvents.stream()
                .filter(e -> {
                    try {
                        var data = objectMapper.readValue(e.getData(), Map.class);
                        return models.contains(data.get("model"));
                    } catch (Exception ex) { return false; }
                }).toList();

            long totalCalls = providerCosts.size();
            double avgLatency = providerCosts.stream()
                .mapToInt(e -> {
                    try {
                        var data = objectMapper.readValue(e.getData(), Map.class);
                        return data.get("latency_ms") instanceof Number n ? n.intValue() : 0;
                    } catch (Exception ex) { return 0; }
                }).average().orElse(0);

            long errorCount = errorEvents.stream()
                .filter(e -> {
                    try {
                        var data = objectMapper.readValue(e.getData(), Map.class);
                        String msg = (String) data.getOrDefault("message", "");
                        return models.stream().anyMatch(msg::contains);
                    } catch (Exception ex) { return false; }
                }).count();

            String lastSuccess = providerCosts.isEmpty() ? null
                : providerCosts.getFirst().getTimestamp().toString();

            Map<String, Object> health = new LinkedHashMap<>();
            health.put("providerId", p.getId());
            health.put("name", p.getName());
            health.put("active", p.isActive());
            health.put("totalCalls", totalCalls);
            health.put("avgLatencyMs", Math.round(avgLatency));
            health.put("errorCount", errorCount);
            health.put("errorRate", totalCalls > 0 ? String.format("%.1f%%", (errorCount * 100.0) / totalCalls) : "0%");
            health.put("lastSuccessfulCall", lastSuccess);
            return health;
        }).collect(Collectors.toList());
    }

    record TestResult(boolean success, String message) {}
}
