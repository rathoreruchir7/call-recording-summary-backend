package dev.callsummary;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "demo", matchIfMissing = true)
public class DemoSpeechToTextProvider implements SpeechToTextProvider {
    @Override
    public String transcribe(InputStream audioStream, String contentType) throws IOException {
        if (audioStream.read() == -1) {
            throw new IOException("Recording is empty");
        }
        return "Demo transcript: the caller discussed the project timeline.";
    }
}
