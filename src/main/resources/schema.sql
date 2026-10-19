CREATE TABLE IF NOT EXISTS calls (
  call_id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  recording_object_key TEXT NOT NULL UNIQUE,
  content_type TEXT NOT NULL,
  size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
  sha256 CHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (
    status IN (
      'UPLOADED',
      'TRANSCRIBING',
      'SUMMARIZING',
      'READY',
      'FAILED'
    )
  ),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS calls_user_created_idx ON calls(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS summaries (
  summary_id UUID PRIMARY KEY,
  call_id UUID NOT NULL UNIQUE REFERENCES calls(call_id),
  user_id UUID NOT NULL,
  transcript TEXT,
  summary_text TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);