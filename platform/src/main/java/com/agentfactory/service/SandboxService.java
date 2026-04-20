package com.agentfactory.service;

import com.agentfactory.config.DockerConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class SandboxService {

    private static final Logger log = LoggerFactory.getLogger(SandboxService.class);
    private static final String SANDBOX_IMAGE = "python:3.13-slim";
    private static final Path SANDBOX_BASE = Path.of(System.getProperty("java.io.tmpdir"), "af-sandboxes");

    private final DockerClient dockerClient;
    private final DockerConfig dockerConfig;
    private final ObjectMapper objectMapper;

    public SandboxService(DockerClient dockerClient, DockerConfig dockerConfig, ObjectMapper objectMapper) {
        this.dockerClient = dockerClient;
        this.dockerConfig = dockerConfig;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void cleanupOrphans() {
        try {
            if (Files.exists(SANDBOX_BASE)) {
                try (var dirs = Files.list(SANDBOX_BASE)) {
                    dirs.forEach(dir -> {
                        try {
                            deleteDirectory(dir);
                            log.info("Cleaned orphan sandbox dir: {}", dir);
                        } catch (Exception e) {
                            log.warn("Failed to clean orphan: {}", dir, e);
                        }
                    });
                }
            }
            Files.createDirectories(SANDBOX_BASE);
        } catch (IOException e) {
            log.warn("Sandbox cleanup failed: {}", e.getMessage());
        }
    }

    public SandboxContext createSandbox(Long taskId, String agentType) {
        Path workDir = SANDBOX_BASE.resolve("task-" + taskId);
        try {
            Files.createDirectories(workDir);

            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withCpuCount(dockerConfig.getCpuLimit())
                    .withMemory(dockerConfig.getMemoryLimitBytes())
                    .withBinds(new Bind(workDir.toString(), new Volume("/workspace")))
                    .withNetworkMode("bridge");

            CreateContainerResponse container = dockerClient.createContainerCmd(SANDBOX_IMAGE)
                    .withName("af-task-" + taskId)
                    .withHostConfig(hostConfig)
                    .withWorkingDir("/workspace")
                    .withCmd("tail", "-f", "/dev/null")
                    .exec();

            dockerClient.startContainerCmd(container.getId()).exec();
            log.info("Created sandbox container {} for task {}", container.getId(), taskId);

            return new SandboxContext(container.getId(), workDir);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create sandbox for task " + taskId, e);
        }
    }

    public void writeInput(SandboxContext ctx, Map<String, Object> taskDef, String apiKey) {
        try {
            Path inputFile = ctx.workDir().resolve("input.json");
            Path tempFile = ctx.workDir().resolve("input.json.tmp");
            objectMapper.writeValue(tempFile.toFile(), taskDef);
            Files.move(tempFile, inputFile, StandardCopyOption.ATOMIC_MOVE);
            inputFile.toFile().setReadable(true, true);
            inputFile.toFile().setWritable(true, true);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write sandbox input", e);
        }
    }

    public Map<String, Object> pollForOutput(SandboxContext ctx, int timeoutSeconds) {
        Path outputFile = ctx.workDir().resolve("output.json");
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);

        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(outputFile)) {
                try {
                    return objectMapper.readValue(outputFile.toFile(), Map.class);
                } catch (IOException e) {
                    log.warn("Failed to read output.json, retrying: {}", e.getMessage());
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Polling interrupted", e);
            }
        }
        throw new RuntimeException("Sandbox timeout: no output after " + timeoutSeconds + "s");
    }

    public void destroySandbox(SandboxContext ctx) {
        try {
            dockerClient.stopContainerCmd(ctx.containerId()).withTimeout(5).exec();
        } catch (Exception e) {
            log.warn("Stop container failed (may already be stopped): {}", e.getMessage());
        }
        try {
            dockerClient.removeContainerCmd(ctx.containerId()).withForce(true).exec();
        } catch (Exception e) {
            log.warn("Remove container failed: {}", e.getMessage());
        }
        try {
            deleteDirectory(ctx.workDir());
        } catch (Exception e) {
            log.warn("Cleanup workdir failed: {}", e.getMessage());
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
        }
    }

    public record SandboxContext(String containerId, Path workDir) {}
}
