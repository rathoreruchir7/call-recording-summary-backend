package dev.callsummary;

import java.io.IOException;
import java.io.InputStream;

public interface SpeechToTextProvider {
    String transcribe(InputStream audioStream, String contentType) throws IOException;
}
