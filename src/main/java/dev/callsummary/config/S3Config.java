package dev.callsummary.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
public class S3Config {
    @Bean
    public S3Client s3Client(
            @Value("${app.s3-endpoint}") String endpoint,
            @Value("${app.s3-access-key}") String accessKey,
            @Value("${app.s3-secret-key}") String secretKey,
            @Value("${app.s3-region}") String region,
            @Value("${app.s3-force-path-style}") boolean forcePathStyle,
            @Value("${app.s3-chunked-encoding-enabled}") boolean chunkedEncodingEnabled) {
        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(forcePathStyle)
                .chunkedEncodingEnabled(chunkedEncodingEnabled)
                .build();

        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(serviceConfiguration)
                .build();
    }
}