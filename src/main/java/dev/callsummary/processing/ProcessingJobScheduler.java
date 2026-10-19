package dev.callsummary.processing;

import dev.callsummary.repository.ProcessingJobRepository;


import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProcessingJobScheduler {
    private final ProcessingJobRepository jobRepository;
    private final CallProcessingService processingService;

    public ProcessingJobScheduler(
            ProcessingJobRepository jobRepository,
            CallProcessingService processingService) {
        this.jobRepository = jobRepository;
        this.processingService = processingService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedJobs() {
        jobRepository.requeueRunningJobs();
    }

    @Scheduled(fixedDelayString = "${app.processing-poll-delay-ms:3000}")
    public void processPendingJobs() {
        jobRepository.findPending(2)
                .forEach(job -> processingService.process(job.callId(), job.userId()));
    }
}
