package com.agentfactory.config;

import com.agentfactory.service.S3StorageService;
import com.agentfactory.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Value("${af.storage.endpoint}")
    private String endpoint;

    @Value("${af.storage.access-key}")
    private String accessKey;

    @Value("${af.storage.secret-key}")
    private String secretKey;

    @Value("${af.storage.bucket}")
    private String bucket;

    @Value("${af.storage.region}")
    private String region;

    @Bean
    public S3Client s3Client() {
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey));
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(credentials)
                .region(Region.of(region))
                .forcePathStyle(true)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey));
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(credentials)
                .region(Region.of(region))
                .build();
    }

    @Bean
    public StorageService storageService(S3Client s3Client, S3Presigner s3Presigner) {
        try {
            ensureBucketExists(s3Client);
        } catch (Exception e) {
            log.warn("MinIO/S3 not available at startup: {}. Bucket will be created on first use.", e.getMessage());
        }
        return new S3StorageService(s3Client, s3Presigner, bucket);
    }

    private void ensureBucketExists(S3Client s3Client) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("Storage bucket '{}' exists", bucket);
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created storage bucket '{}'", bucket);
        }
    }
}
