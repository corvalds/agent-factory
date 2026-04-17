package com.agentfactory.service;

import com.agentfactory.model.Provider;
import com.agentfactory.model.TaskStatus;
import com.agentfactory.repository.ProviderRepository;
import com.agentfactory.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class ProviderService {

    private final ProviderRepository providerRepository;
    private final TaskRepository taskRepository;
    private final EncryptionService encryptionService;

    public ProviderService(ProviderRepository providerRepository,
                           TaskRepository taskRepository,
                           EncryptionService encryptionService) {
        this.providerRepository = providerRepository;
        this.taskRepository = taskRepository;
        this.encryptionService = encryptionService;
    }

    public List<Provider> findAll() {
        return providerRepository.findAll();
    }

    public List<Provider> findActive() {
        return providerRepository.findByActiveTrue();
    }

    public Provider findById(Long id) {
        return providerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + id));
    }

    public Provider create(Provider provider) {
        provider.setApiKey(encryptionService.encrypt(provider.getApiKey()));
        return providerRepository.save(provider);
    }

    public Provider update(Long id, Provider updated) {
        Provider existing = findById(id);
        existing.setName(updated.getName());
        existing.setType(updated.getType());
        existing.setBaseUrl(updated.getBaseUrl());
        existing.setModels(updated.getModels());
        existing.setActive(updated.isActive());
        if (updated.getApiKey() != null && !updated.getApiKey().isBlank()) {
            existing.setApiKey(encryptionService.encrypt(updated.getApiKey()));
        }
        return providerRepository.save(existing);
    }

    public void delete(Long id) {
        Provider provider = findById(id);
        List<String> modelIds = parseModels(provider.getModels());
        if (taskRepository.existsByModelIdInAndStatus(modelIds, TaskStatus.RUNNING)) {
            throw new IllegalStateException("Cannot delete provider while tasks are running with its models");
        }
        providerRepository.delete(provider);
    }

    public String decryptApiKey(Long id) {
        Provider provider = findById(id);
        return encryptionService.decrypt(provider.getApiKey());
    }

    private List<String> parseModels(String models) {
        if (models == null || models.isBlank()) return List.of();
        return Arrays.stream(models.split(","))
                .map(String::trim)
                .toList();
    }
}
