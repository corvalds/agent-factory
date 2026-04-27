package com.agentfactory.controller;

import com.agentfactory.model.Artifact;
import com.agentfactory.model.ArtifactType;
import com.agentfactory.service.ArtifactService;
import com.agentfactory.service.StorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
public class ArtifactController {

    private final ArtifactService artifactService;
    private final StorageService storageService;

    public ArtifactController(ArtifactService artifactService, StorageService storageService) {
        this.artifactService = artifactService;
        this.storageService = storageService;
    }

    @PostMapping("/api/tasks/{taskId}/artifacts")
    @ResponseStatus(HttpStatus.CREATED)
    public ArtifactResponse upload(@PathVariable Long taskId,
                                   @RequestParam("file") MultipartFile file,
                                   @RequestParam(value = "mime_type", required = false) String mimeType,
                                   @RequestParam(value = "artifact_type", defaultValue = "SUPPLEMENTARY") String artifactTypeStr) {
        String resolvedMime = mimeType != null ? mimeType : file.getContentType();
        ArtifactType artifactType;
        try {
            artifactType = ArtifactType.valueOf(artifactTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            artifactType = ArtifactType.SUPPLEMENTARY;
        }
        Artifact artifact = artifactService.store(taskId, file, resolvedMime, artifactType);
        return ArtifactResponse.from(artifact);
    }

    @GetMapping("/api/tasks/{taskId}/artifacts")
    public List<ArtifactResponse> list(@PathVariable Long taskId) {
        return artifactService.listByTask(taskId).stream()
                .map(ArtifactResponse::from)
                .toList();
    }

    @GetMapping("/api/artifacts/{artifactId}/content")
    public ResponseEntity<StreamingResponseBody> content(@PathVariable UUID artifactId) {
        Artifact artifact = artifactService.getById(artifactId);
        StreamingResponseBody body = outputStream -> {
            try (InputStream is = storageService.retrieve(artifact.getStorageKey())) {
                is.transferTo(outputStream);
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + artifact.getFilename() + "\"")
                .contentType(MediaType.parseMediaType(artifact.getMimeType()))
                .body(body);
    }

    @GetMapping("/api/tasks/{taskId}/artifacts/download")
    public ResponseEntity<StreamingResponseBody> downloadAll(@PathVariable Long taskId) {
        List<Artifact> artifacts = artifactService.listByTask(taskId);
        if (artifacts.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        StreamingResponseBody body = outputStream -> {
            try (var zip = new ZipOutputStream(outputStream)) {
                Set<String> usedNames = new HashSet<>();
                for (Artifact artifact : artifacts) {
                    String name = artifact.getFilename();
                    if (!usedNames.add(name)) {
                        String base = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
                        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : "";
                        name = base + "-" + artifact.getId().toString().substring(0, 8) + ext;
                    }
                    zip.putNextEntry(new ZipEntry(name));
                    try (InputStream is = storageService.retrieve(artifact.getStorageKey())) {
                        is.transferTo(zip);
                    }
                    zip.closeEntry();
                }
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"task-" + taskId + "-artifacts.zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }

    @DeleteMapping("/api/artifacts/{artifactId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID artifactId) {
        artifactService.delete(artifactId);
    }

    record ArtifactResponse(UUID id, String filename, String mimeType, long sizeBytes,
                            String artifactType, int sortOrder, String createdAt) {
        static ArtifactResponse from(Artifact a) {
            return new ArtifactResponse(
                    a.getId(), a.getFilename(), a.getMimeType(), a.getSizeBytes(),
                    a.getArtifactType().name(), a.getSortOrder(), a.getCreatedAt().toString());
        }
    }
}
