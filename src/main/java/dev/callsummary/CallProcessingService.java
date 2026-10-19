package dev.callsummary;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@Service
public class CallProcessingService {
    private static final Logger log = LoggerFactory.getLogger(CallProcessingService.class);
    private final CallRepository callRepository;
    private final SummaryRepository summaryRepository;
    private final ProcessingJobRepository jobRepository;
    private final SpeechToTextProvider speechToTextProvider;
    private final SummaryGenerator summaryGenerator;
    private final LlmBudgetPolicy llmBudgetPolicy;
    private final S3Client s3;
    private final String bucket;
    private final int maxTranscriptCharacters;

    public CallProcessingService(CallRepository callRepository, SummaryRepository summaryRepository,
            ProcessingJobRepository jobRepository, SpeechToTextProvider speechToTextProvider,
            SummaryGenerator summaryGenerator, LlmBudgetPolicy llmBudgetPolicy, S3Client s3,
            @Value("${app.bucket}") String bucket,
            @Value("${app.max-transcript-characters}") int maxTranscriptCharacters) {
        this.callRepository = callRepository;
        this.summaryRepository = summaryRepository;
        this.jobRepository = jobRepository;
        this.speechToTextProvider = speechToTextProvider;
        this.summaryGenerator = summaryGenerator;
        this.llmBudgetPolicy = llmBudgetPolicy;
        this.s3 = s3;
        this.bucket = bucket;
        this.maxTranscriptCharacters = maxTranscriptCharacters;
    }

    @Async("processingExecutor")
    public void process(UUID callId, UUID userId) {
        if (!jobRepository.claim(callId, userId)) return;
        try {
            callRepository.updateStatus(callId, userId, "TRANSCRIBING");
            CallRecord call = callRepository.find(callId, userId).orElseThrow(CallNotFoundException::new);
            String objectKey = callRepository.findRecordingObjectKey(callId, userId).orElseThrow(CallNotFoundException::new);
            String transcript;
            try (ResponseInputStream<GetObjectResponse> audioStream = s3.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(objectKey).build())) {
                transcript = speechToTextProvider.transcribe(audioStream, call.contentType());
            }
            if (transcript.length() > maxTranscriptCharacters) throw new TranscriptTooLargeException();

            callRepository.updateStatus(callId, userId, "SUMMARIZING");
            LlmRequestPlan plan = llmBudgetPolicy.validate(transcript);
            String summary = summaryGenerator.summarize(transcript, plan.maxOutputTokens());
            summaryRepository.save(callId, userId, transcript, summary);
            callRepository.updateStatus(callId, userId, "READY");
            jobRepository.markSucceeded(callId, userId);
        } catch (Exception exception) {
            log.warn("Call processing failed for {}", callId, exception);
            callRepository.updateStatus(callId, userId, "FAILED");
            jobRepository.recordFailure(callId, userId, failureMessage(exception), isRetryable(exception));
        }
    }

    private boolean isRetryable(Exception exception) {
        if (exception instanceof RestClientResponseException responseError) {
            String response = responseError.getResponseBodyAsString();
            if (response.contains("credit_balance_exhausted")) return false;
            return responseError.getStatusCode().is5xxServerError()
                    || responseError.getStatusCode().value() == 429;
        }
        return true;
    }
    private String failureMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}


