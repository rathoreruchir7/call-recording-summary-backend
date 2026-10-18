# Spring Boot call recording backend — milestone 1

This is a **new Spring Boot project**, separate from the earlier Node.js example. It implements only the first milestone: upload an audio recording, store it in S3-compatible MinIO, save metadata in PostgreSQL, and retrieve the call record. It does **not** transcribe or summarize yet.

## Small design

`POST /call` receives a multipart field named `file`. Spring limits the upload to 20 MB. The service checks size and declared audio MIME type, generates a call ID and private S3 object key, and sends the multipart input stream to S3 using its known length. **Only after S3's `putObject` returns successfully** does it insert the metadata row in PostgreSQL. If the database insert fails, it attempts to delete the S3 object. `GET /call/{id}` reads the row and returns metadata, never a public S3 URL.

The database row has `call_id`, `user_id`, `recording_object_key`, `content_type`, `size_bytes`, `sha256`, `status`, and `created_at`. For this learning milestone, one fixed demo user ID is configured; there is no real authentication. The status is `UPLOADED` because background processing is a later milestone.

## Run

Requires Docker Desktop with its engine running. The first build compiles MinIO from its pinned official source release and may take several minutes. From this directory:

```powershell
docker compose up --build
```

The API is at `http://localhost:8080`, and MinIO's local console is at `http://localhost:9001` (`localminio` / `change-this-local-secret`). In another terminal, upload a WAV file:

```powershell
curl.exe -X POST -F "file=@C:\path\to\recording.wav;type=audio/wav" http://localhost:8080/call
```

The response contains `callId`. Read it with:

```powershell
curl.exe http://localhost:8080/call/YOUR_CALL_ID
```

Run unit tests with `mvn test` if Maven is installed. The Docker build runs compilation; the current environment could not run Docker because its engine was unavailable, and Maven was not installed, so the complete application has **not yet been verified end to end**.

## Boundaries for the next milestone

The MIME type is client supplied; production validation should inspect the actual audio bytes and duration before storage. This milestone also lacks authentication, upload concurrency quotas, retry reconciliation, and the transcription/summary worker. Those are explicit next steps, not hidden features. MinIO credentials and images here are only for local development.
