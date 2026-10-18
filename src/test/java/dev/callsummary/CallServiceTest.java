package dev.callsummary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class CallServiceTest {
    private final S3Client s3 = mock(S3Client.class);
    private final CallRepository repository = mock(CallRepository.class);
    private final CallService service = new CallService(s3, repository, "test-bucket", UUID.randomUUID());

    @Test
    void savesMetadataOnlyAfterS3Acknowledges() throws Exception {
        byte[] bytes = "example audio".getBytes();
        var file = new MockMultipartFile("file", "call.wav", "audio/wav", bytes);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(repository.find(any(UUID.class), any(UUID.class))).thenReturn(Optional.of(
                new CallRecord(UUID.randomUUID(), "UPLOADED", bytes.length, "audio/wav", null)));

        assertEquals("UPLOADED", service.upload(file).status());
        InOrder order = inOrder(s3, repository);
        order.verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        order.verify(repository).insert(any(UUID.class), any(UUID.class), any(String.class),
                any(String.class), org.mockito.ArgumentMatchers.eq((long) bytes.length), any(String.class));
    }

    @Test
    void rejectsEmptyFileWithoutCallingS3() {
        var file = new MockMultipartFile("file", "empty.wav", "audio/wav", new byte[0]);
        assertThrows(InvalidRecordingException.class, () -> service.upload(file));
        verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void doesNotSaveMetadataWhenS3Fails() {
        var file = new MockMultipartFile("file", "call.wav", "audio/wav", "audio".getBytes());
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new IllegalStateException("S3 unavailable"));

        assertThrows(IllegalStateException.class, () -> service.upload(file));
        verify(repository, never()).insert(any(UUID.class), any(UUID.class), any(String.class),
                any(String.class), org.mockito.ArgumentMatchers.anyLong(), any(String.class));
    }
}
