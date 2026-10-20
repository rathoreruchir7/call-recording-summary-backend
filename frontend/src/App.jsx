import { useEffect, useState } from "react";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export default function App() {
  const [file, setFile] = useState(null);
  const [calls, setCalls] = useState([]);
  const [selectedCall, setSelectedCall] = useState(null);
  const [summary, setSummary] = useState(null);
  const [message, setMessage] = useState("Choose an audio file to upload.");
  const [busy, setBusy] = useState(false);

  async function loadCalls() {
    const response = await fetch(`${API_BASE_URL}/call`);
    if (!response.ok) throw new Error("Could not load calls");
    const data = await response.json();
    setCalls(data.calls ?? []);
  }

  useEffect(() => {
    loadCalls().catch((error) => setMessage(error.message));
  }, []);

  async function getSummary(callId) {
    const response = await fetch(`${API_BASE_URL}/call/${callId}/summary`);
    if (response.status === 409) return null;
    if (!response.ok) throw new Error("Could not load the summary");
    return response.json();
  }

  async function pollCall(callId) {
    const response = await fetch(`${API_BASE_URL}/call/${callId}`);
    if (!response.ok) throw new Error("Could not load call status");
    const call = await response.json();
    setSelectedCall(call);
    await loadCalls();

    if (call.status === "READY") {
      setSummary(await getSummary(callId));
      setMessage("Summary is ready.");
      return;
    }
    if (call.status === "FAILED") {
      setMessage("Processing failed. Check the backend job record for details.");
      return;
    }

    setMessage(`Processing: ${call.status}`);
    window.setTimeout(() => {
      pollCall(callId).catch((error) => setMessage(error.message));
    }, 2000);
  }

  async function upload(event) {
    event.preventDefault();
    if (!file) return;

    setBusy(true);
    setSummary(null);
    setMessage("Uploading recording...");
    try {
      const formData = new FormData();
      formData.append("file", file);
      const response = await fetch(`${API_BASE_URL}/call`, {
        method: "POST",
        body: formData
      });
      const call = await response.json();
      if (!response.ok) throw new Error(call.error ?? "Upload failed");
      setSelectedCall(call);
      setMessage("Upload complete. Waiting for transcription and summary...");
      pollCall(call.callId).catch((error) => setMessage(error.message));
    } catch (error) {
      setMessage(error.message);
    } finally {
      setBusy(false);
    }
  }

  function chooseCall(call) {
    setSelectedCall(call);
    setSummary(null);
    pollCall(call.callId).catch((error) => setMessage(error.message));
  }

  return (
    <main>
      <header>
        <p className="eyebrow">Spring Boot + MinIO + PostgreSQL + Groq</p>
        <h1>Call Summary</h1>
        <p>Upload a recording and follow its transcript and summary processing.</p>
      </header>

      <section className="panel">
        <h2>Upload recording</h2>
        <form onSubmit={upload}>
          <input
            type="file"
            accept="audio/wav,audio/x-wav,audio/mpeg,audio/mp4,audio/m4a,audio/x-m4a,video/mp4,audio/ogg"
            onChange={(event) => setFile(event.target.files[0] ?? null)}
          />
          <button type="submit" disabled={!file || busy}>
            {busy ? "Uploading..." : "Upload and summarize"}
          </button>
        </form>
        <p className="message">{message}</p>
      </section>

      <section className="layout">
        <div className="panel">
          <div className="section-title">
            <h2>Calls</h2>
            <button className="secondary" onClick={() => loadCalls().catch((error) => setMessage(error.message))}>
              Refresh
            </button>
          </div>
          {calls.length === 0 ? <p>No recordings uploaded yet.</p> : (
            <ul className="call-list">
              {calls.map((call) => (
                <li key={call.callId}>
                  <button className="call-button" onClick={() => chooseCall(call)}>
                    <strong>{call.contentType}</strong>
                    <span>{Math.round(call.sizeBytes / 1024)} KB</span>
                    <span className={`status ${call.status.toLowerCase()}`}>{call.status}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="panel">
          <h2>Summary</h2>
          {!selectedCall ? <p>Select or upload a recording.</p> : (
            <>
              <p><strong>Status:</strong> {selectedCall.status}</p>
              {summary && (
                <>
                  <h3>Transcript</h3>
                  <p className="content">{summary.transcript}</p>
                  <h3>Generated summary</h3>
                  <p className="content">{summary.summaryText}</p>
                </>
              )}
            </>
          )}
        </div>
      </section>
    </main>
  );
}