# Deploy to Render

This project consists of three runtime parts:

1. the Spring Boot API,
2. PostgreSQL for call, summary, and job records, and
3. S3-compatible object storage for uploaded recordings.

The local Docker Compose setup is only for development. Do not use its `localhost` PostgreSQL or MinIO addresses in Render.

## Before deployment

Push the latest project changes to GitHub. Keep API keys out of Git. The backend reads all production credentials from Render environment variables.

## 1. Create PostgreSQL

In Render, create a PostgreSQL database in the same region you will use for the API. Copy its **internal** host, database name, username, and password from the database connection details.

Set these backend variables from those values:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://<internal-host>:5432/<database-name>
DATABASE_USER=<database-user>
DATABASE_PASSWORD=<database-password>
```

The `jdbc:postgresql://` prefix is required by Spring Boot. Do not paste a `postgres://` URL directly into `SPRING_DATASOURCE_URL`.

## 2. Host object storage

Use one of these production options:

- Deploy MinIO on Render with a persistent disk, keeping it in the same region as the API.
- Use an external S3-compatible provider such as Amazon S3 or Cloudflare R2.

For a Render-hosted MinIO service, set these API variables using its endpoint and credentials:

```text
S3_ENDPOINT=http://<minio-internal-host>:9000
S3_BUCKET=call-recordings
S3_ACCESS_KEY=<minio-root-user>
S3_SECRET_KEY=<minio-root-password>
```

The storage must be persistent. Render service files are otherwise removed during deploys and restarts.

## 3. Create the Spring Boot web service

Create a new Render **Web Service** from this repository:

- Runtime: Docker
- Dockerfile: `./Dockerfile`
- Root directory: leave blank

Add these environment variables:

```text
AI_PROVIDER=groq
GROQ_API_KEY=<your-private-key>
SPRING_DATASOURCE_URL=jdbc:postgresql://<internal-host>:5432/<database-name>
DATABASE_USER=<database-user>
DATABASE_PASSWORD=<database-password>
S3_ENDPOINT=<object-storage-endpoint>
S3_BUCKET=call-recordings
S3_ACCESS_KEY=<object-storage-access-key>
S3_SECRET_KEY=<object-storage-secret-key>
CORS_ALLOWED_ORIGINS=http://localhost:5173
```

Render provides `PORT` automatically. The application is configured to use it.

After deployment, open:

```text
https://<your-api-service>.onrender.com/call
```

A JSON call list confirms that the API is running.

## 4. Create the React static site

Create a Render **Static Site** from the same repository:

- Root directory: `frontend`
- Build command: `npm ci && npm run build`
- Publish directory: `dist`
- Build environment variable: `VITE_API_BASE_URL=https://<your-api-service>.onrender.com`

Deploy it once to obtain its public URL. Then change the API service variable:

```text
CORS_ALLOWED_ORIGINS=https://<your-frontend-site>.onrender.com
```

Save and redeploy the API. The frontend can now call the API from its own domain.

## Verify

1. Open the frontend URL.
2. Upload a small WAV, MP3, M4A, MP4, or OGG recording.
3. Wait for status `READY`.
4. Open the summary.
5. In Render logs, confirm that the background job completed.

## Security

Never commit `GROQ_API_KEY`, database passwords, or S3 credentials. Add and change them only in Render's Environment page.