package dev.callsummary;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@Service
public class CallProcessingService {

    private final CallRepository callRepository;
    private final SummaryRepository summaryRepository;
    private final SpeechToTextProvider speechToTextProvider;
    private final SummaryGenerator summaryGenerator;
    private final S3Client s3;
    private final String bucket;
    private final int maxTranscriptCharacters;

    public CallProcessingService(
            CallRepository callRepository,
            SummaryRepository summaryRepository,
            SpeechToTextProvider speechToTextProvider,
            SummaryGenerator summaryGenerator,
            S3Client s3,
            @Value("${app.bucket}") String bucket,
            @Value("${app.max-transcript-characters}") int maxTranscriptCharacters) {
        this.callRepository = callRepository;
        this.summaryRepository = summaryRepository;
        this.speechToTextProvider = speechToTextProvider;
        this.summaryGenerator = summaryGenerator;
        this.s3 = s3;
        this.bucket = bucket;
        this.maxTranscriptCharacters = maxTranscriptCharacters;
    }

    @Async
    public void process(UUID callId, UUID userId) {
        try {
            callRepository.updateStatus(callId, userId, "TRANSCRIBING");

            CallRecord call = callRepository.find(callId, userId).orElseThrow(CallNotFoundException::new);
            String objectKey = callRepository.findRecordingObjectKey(callId, userId)
                    .orElseThrow(CallNotFoundException::new);

            String transcript;
            try (ResponseInputStream<GetObjectResponse> audioStream = s3.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(objectKey).build())) {
                transcript = speechToTextProvider.transcribe(audioStream, call.contentType());
            }

            if (transcript.length() > maxTranscriptCharacters) {
                throw new TranscriptTooLargeException();
            }

            callRepository.updateStatus(callId, userId, "SUMMARIZING");
            String summary = summaryGenerator.summarize(transcript);

            summaryRepository.save(callId, userId, transcript, summary);
            callRepository.updateStatus(callId, userId, "READY");
        } catch (Exception exception) {
            callRepository.updateStatus(callId, userId, "FAILED");
        }
    }
}
