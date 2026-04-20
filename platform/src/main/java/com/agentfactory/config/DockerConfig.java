package com.agentfactory.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class DockerConfig {

    @Value("${af.sandbox.docker-host:unix:///var/run/docker.sock}")
    private String dockerHost;

    @Value("${af.sandbox.cpu-limit:1}")
    private long cpuLimit;

    @Value("${af.sandbox.memory-limit-mb:512}")
    private long memoryLimitMb;

    @Value("${af.sandbox.timeout-seconds:300}")
    private int timeoutSeconds;

    @Bean
    public DockerClient dockerClient() {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();

        var httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .maxConnections(10)
                .connectionTimeout(Duration.ofSeconds(5))
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }

    public long getCpuLimit() { return cpuLimit; }
    public long getMemoryLimitBytes() { return memoryLimitMb * 1024 * 1024; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
}
