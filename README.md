# Call recording summary backend

A learning project that simulates an Android call-recording sync backend. It is not Samsung proprietary code.

The project accepts an audio upload, stores it in S3-compatible MinIO, saves its metadata in PostgreSQL, then starts background processing. The current transcription and summary providers are local demos, so the project runs without an AI key or paid provider.

## Flow

```text
Client POST /call
  → Spring Boot validates the upload and streams it to MinIO
  → PostgreSQL stores the call metadata and private object key
  → background processing reads the recording stream back from MinIO
  → speech-to-text provider returns a transcript
  → transcript size limit is checked
  → summary provider creates a summary
  → PostgreSQL stores transcript and summary
```

The call status moves through:

```text
UPLOADED → TRANSCRIBING → SUMMARIZING → READY
                              ↘ FAILED
```

## Local setup

Start PostgreSQL and MinIO from the project directory:

```powershell
docker compose up -d postgres minio
```

Then start `CallApplication.java` from VS Code. The API runs at `http://localhost:8080`.

MinIO's local console is `http://localhost:9001`.

- Username: `localminio`
- Password: `change-this-local-secret`

These are local development credentials only.

## API requests

### Upload a recording

`POST http://localhost:8080/call`

In Postman, choose **Body → form-data** and add a key named `file`. Change its type from Text to File, then choose a WAV, MP3, M4A, or OGG file up to 20 MB.

The response returns a `callId` and normally begins with status `UPLOADED`.

### List calls

`GET http://localhost:8080/call`

### Get one call and its processing status

`GET http://localhost:8080/call/{callId}`

### Get transcript and summary

`GET http://localhost:8080/call/{callId}/summary`

A request made before processing finishes receives HTTP `409` with `Summary is not ready yet`.

## Current safety controls

- Spring rejects uploads over 20 MB.
- The API permits declared WAV, MP3, M4A, and OGG content types.
- Audio is streamed to MinIO instead of first being loaded as one large byte array.
- The transcript is capped at 12,000 characters by `app.max-transcript-characters`.
- A processing failure sets the call status to `FAILED` and prevents a summary from being saved.

The transcript cap is a first guard. A real LLM integration should next estimate input tokens and cost, reserve a budget before sending, cap output tokens, split permitted long transcripts into chunks, and retry failures with a durable queue.

## Provider design

`SpeechToTextProvider` and `SummaryGenerator` are interfaces. The `DemoSpeechToTextProvider` and `DemoSummaryGenerator` implementations are safe local placeholders. The OpenAI and Groq implementations are selected through configuration and keep credentials in environment variables, never in source code.

## Known limits

This project uses one configured demo user and has no authentication. It also does not yet inspect actual audio duration or file bytes, provide user authentication, or use a durable external queue when deployed across multiple application instances.
## Optional hosted OpenAI mode

The default is `AI_PROVIDER=demo`, which makes no external AI request. To use real transcription and summarization, set these environment variables before starting the application:

```powershell
$env:AI_PROVIDER = "openai"
$env:OPENAI_API_KEY = "your_key_here"
$env:OPENAI_TRANSCRIPTION_MODEL = "gpt-transcribe"
$env:OPENAI_SUMMARY_MODEL = "gpt-5.2"
```

Do not put the API key in `application.yml`, Git, Postman, or this README. The OpenAI providers stream the recording from MinIO to the transcription endpoint and send only the resulting transcript to the summary endpoint. The Responses request sets `store: false`.

For the default `gpt-5.2` summary model, the configured prices are $1.75 per million input tokens and $14 per million output tokens. If you choose another model, set `LLM_INPUT_COST_PER_MILLION_TOKENS` and `LLM_OUTPUT_COST_PER_MILLION_TOKENS` to that model’s current prices before starting the application. The app uses them with the configured input, output, and per-call limits to reject an over-budget request before the summary API call.


## Optional hosted Groq mode

Groq mode uses hosted speech-to-text and a hosted text model. Create a Groq API key, keep it outside the repository, and set these environment variables before starting the application:

```powershell
$env:AI_PROVIDER = "groq"
$env:GROQ_API_KEY = "your_key_here"
$env:GROQ_TRANSCRIPTION_MODEL = "whisper-large-v3-turbo"
$env:GROQ_SUMMARY_MODEL = "openai/gpt-oss-20b"
```

The implementation sends audio to Groq's transcription endpoint and then sends the resulting transcript to Groq's chat-completions endpoint. Groq free-tier limits can still return HTTP 429, which the existing processing job records and retries according to its retry rules.
## React testing UI

A small React UI is in `frontend/`. Keep the Spring Boot application and Docker services running, then open a second terminal in that folder:

```powershell
cd frontend
npm install
npm run dev
```

Open the URL printed by Vite, normally `http://localhost:5173`. The UI can upload audio, display processing status, list calls, and show the transcript and summary. Spring Boot permits this local browser origin through `WebConfig`.
