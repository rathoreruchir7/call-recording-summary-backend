package dev.callsummary;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.stereotype.Component;

@Component
public class DemoSpeechToTextProvider implements SpeechToTextProvider {
    @Override
    public String transcribe(InputStream audioStream, String contentType) throws IOException {
        if (audioStream.read() == -1) {
            throw new IOException("Recording is empty");
        }
        return "Demo transcript: the caller discussed the project timeline.";
    }
}
