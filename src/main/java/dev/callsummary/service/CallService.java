package dev.callsummary.service;

import dev.callsummary.exception.CallNotFoundException;
import dev.callsummary.exception.InvalidRecordingException;
import dev.callsummary.exception.SummaryNotReadyException;
import dev.callsummary.model.CallRecord;
import dev.callsummary.model.SummaryRecord;
import dev.callsummary.repository.CallRepository;
import dev.callsummary.repository.ProcessingJobRepository;
import dev.callsummary.repository.SummaryRepository;


import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class CallService {
    static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("audio/wav", "audio/x-wav", "audio/mpeg", "audio/mp4", "audio/ogg");
    private final S3Client s3;
    private final CallRepository repository;
    private final ProcessingJobRepository jobRepository;
    private final SummaryRepository summaryRepository;
    private final String bucket;
    private final UUID demoUserId;

    public CallService(S3Client s3, CallRepository repository,
            ProcessingJobRepository jobRepository, SummaryRepository summaryRepository,
            @Value("${app.bucket}") String bucket, @Value("${app.demo-user-id}") UUID demoUserId) {
        this.s3 = s3;
        this.repository = repository;
        this.jobRepository = jobRepository;
        this.summaryRepository = summaryRepository;
        this.bucket = bucket;
        this.demoUserId = demoUserId;
    }

    public CallRecord upload(MultipartFile file) throws IOException {
        long bytes = file.getSize();
        String contentType = file.getContentType();
        if (bytes <= 0 || bytes > MAX_BYTES || contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new InvalidRecordingException("Expected nonempty WAV, MP3, M4A, or OGG audio up to 20 MB");
        }
        UUID callId = UUID.randomUUID();
        String key = "calls/" + demoUserId + "/" + callId + "/recording";
        MessageDigest digest = sha256();
        try (InputStream input = new DigestInputStream(file.getInputStream(), digest)) {
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                    RequestBody.fromInputStream(input, bytes));
        }
        try {
            repository.insert(callId, demoUserId, key, contentType, bytes, HexFormat.of().formatHex(digest.digest()));
            jobRepository.create(callId, demoUserId);
        } catch (RuntimeException databaseError) {
            try {
                s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            } catch (RuntimeException cleanupError) {
                databaseError.addSuppressed(cleanupError);
            }
            throw databaseError;
        }
        return repository.find(callId, demoUserId).orElseThrow();
    }

    public CallRecord get(UUID callId) {
        return repository.find(callId, demoUserId).orElseThrow(CallNotFoundException::new);
    }

    public SummaryRecord getSummary(UUID callId) {
        get(callId);
        return summaryRepository.findByCallId(callId, demoUserId).orElseThrow(SummaryNotReadyException::new);
    }

    public List<CallRecord> list() {
        return repository.findAll(demoUserId);
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
