package com.agentfactory.service;

import java.io.InputStream;
import java.time.Duration;

public interface StorageService {

    void store(String key, InputStream content, long size, String contentType);

    InputStream retrieve(String key);

    void delete(String key);

    String generatePresignedUrl(String key, Duration expiry);

    boolean isHealthy();
}
