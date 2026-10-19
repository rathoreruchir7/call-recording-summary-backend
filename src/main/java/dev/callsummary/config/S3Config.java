package dev.callsummary.config;


import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.ApplicationRunner;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Configuration
public class S3Config {
    @Bean
    S3Client s3Client(@Value("${app.s3-endpoint}") String endpoint,
                      @Value("${app.s3-access-key}") String accessKey,
                      @Value("${app.s3-secret-key}") String secretKey) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    @Bean
    ApplicationRunner createBucket(S3Client s3, @Value("${app.bucket}") String bucket) {
        return args -> {
            try { s3.headBucket(request -> request.bucket(bucket)); }
            catch (S3Exception error) {
                if (error.statusCode() != 404) throw error;
                s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            }
        };
    }
}
