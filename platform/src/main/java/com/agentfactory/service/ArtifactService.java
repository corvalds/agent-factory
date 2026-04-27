package com.agentfactory.service;

import com.agentfactory.model.Artifact;
import com.agentfactory.model.ArtifactType;
import com.agentfactory.model.Task;
import com.agentfactory.repository.ArtifactRepository;
import com.agentfactory.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class ArtifactService {

    private final ArtifactRepository artifactRepository;
    private final TaskRepository taskRepository;
    private final StorageService storageService;
    private final SseEmitterService sseEmitterService;

    public ArtifactService(ArtifactRepository artifactRepository,
                           TaskRepository taskRepository,
                           StorageService storageService,
                           SseEmitterService sseEmitterService) {
        this.artifactRepository = artifactRepository;
        this.taskRepository = taskRepository;
        this.storageService = storageService;
        this.sseEmitterService = sseEmitterService;
    }

    public Artifact store(Long taskId, MultipartFile file, String mimeType, ArtifactType artifactType) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        try {
            return storeInternal(task, file.getOriginalFilename(), mimeType, file.getSize(), file.getInputStream(), artifactType);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }
    }

    public Artifact storeFromStream(Task task, String filename, String mimeType, long size,
                                    InputStream content, ArtifactType artifactType) {
        return storeInternal(task, filename, mimeType, size, content, artifactType);
    }

    public Artifact storeText(Task task, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return storeInternal(task, "result.md", "text/markdown", bytes.length,
                new ByteArrayInputStream(bytes), ArtifactType.PRIMARY);
    }

    public List<Artifact> listByTask(Long taskId) {
        return artifactRepository.findByTaskIdOrderBySortOrderAsc(taskId);
    }

    public Artifact getById(UUID artifactId) {
        return artifactRepository.findById(artifactId)
                .orElseThrow(() -> new IllegalArgumentException("Artifact not found: " + artifactId));
    }

    public String getPresignedUrl(UUID artifactId) {
        Artifact artifact = getById(artifactId);
        return storageService.generatePresignedUrl(artifact.getStorageKey(), Duration.ofMinutes(5));
    }

    public InputStream getContent(UUID artifactId) {
        Artifact artifact = getById(artifactId);
        return storageService.retrieve(artifact.getStorageKey());
    }

    public void delete(UUID artifactId) {
        Artifact artifact = getById(artifactId);
        String key = artifact.getStorageKey();
        artifactRepository.delete(artifact);
        storageService.delete(key);
    }

    private static final int MAX_ARTIFACTS_PER_TASK = 100;

    private Artifact storeInternal(Task task, String filename, String mimeType, long size,
                                   InputStream content, ArtifactType artifactType) {
        String safeName = sanitizeFilename(filename);
        UUID artifactId = UUID.randomUUID();
        String storageKey = "artifacts/" + task.getId() + "/" + artifactId + "/" + safeName;

        storageService.store(storageKey, content, size, mimeType);

        int nextOrder;
        synchronized (this) {
            int currentCount = artifactRepository.findByTaskIdOrderBySortOrderAsc(task.getId()).size();
            if (currentCount >= MAX_ARTIFACTS_PER_TASK) {
                storageService.delete(storageKey);
                throw new IllegalStateException("Task " + task.getId() + " has reached the maximum of " + MAX_ARTIFACTS_PER_TASK + " artifacts");
            }
            nextOrder = artifactRepository.findMaxSortOrderByTaskId(task.getId()) + 1;

            Artifact artifact = new Artifact();
            artifact.setId(artifactId);
            artifact.setTask(task);
            artifact.setFilename(safeName);
            artifact.setMimeType(mimeType);
            artifact.setSizeBytes(size);
            artifact.setStorageKey(storageKey);
            artifact.setArtifactType(artifactType);
            artifact.setSortOrder(nextOrder);

            try {
                Artifact saved = artifactRepository.save(artifact);
                sseEmitterService.emitArtifactReady(task.getId(), saved.getId().toString());
                return saved;
            } catch (Exception e) {
                storageService.delete(storageKey);
                throw e;
            }
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "unnamed";
        String name = filename.replace("\\", "/");
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) name = name.substring(lastSlash + 1);
        name = name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        if (name.isBlank() || name.startsWith(".")) name = "file_" + name;
        return name.length() > 255 ? name.substring(0, 255) : name;
    }
}
