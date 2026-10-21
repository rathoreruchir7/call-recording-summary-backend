import { useEffect, useRef, useState } from "react";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";
const sizeLabel = (bytes) => bytes < 1024 * 1024 ? `${Math.round(bytes / 1024)} KB` : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;

function AudioGlyph() {
  return <svg viewBox="0 0 36 36" fill="none" aria-hidden="true">
    {[7, 15, 26, 34, 22, 12, 6].map((height, index) =>
      <line key={index} x1={3 + index * 5} x2={3 + index * 5} y1={18 - height / 2} y2={18 + height / 2} />)}
  </svg>;
}

function ProcessingOrb({ message, active, completed }) {
  return <div className={`processing-stage${active ? " active" : ""}${completed ? " completed" : ""}`} role="status" aria-live="polite">
    <div className="orb-wrap" aria-hidden="true">
      <span className="orb-orbit orb-orbit-outer" />
      <span className="orb-orbit orb-orbit-inner" />
      <span className="orb-halo" />
      <span className="orb-core" />
      <span className="orb-ripple orb-ripple-one" />
      <span className="orb-ripple orb-ripple-two" />
      {Array.from({ length: 8 }, (_, index) => <span className="orb-spark" key={index} style={{ "--angle": `${index * 45}deg` }} />)}
      <svg className="orb-check" viewBox="0 0 32 32" fill="none"><path d="m7 16 6 6L25 10" /></svg>
    </div>
    <p>{message}</p>
  </div>;
}

export default function App() {
  const [file, setFile] = useState(null);
  const [calls, setCalls] = useState([]);
  const [selectedCall, setSelectedCall] = useState(null);
  const [summary, setSummary] = useState(null);
  const [displayedSummary, setDisplayedSummary] = useState("");
  const [message, setMessage] = useState("Choose an audio recording to begin.");
  const [busy, setBusy] = useState(false);
  const [polling, setPolling] = useState(false);
  const [completed, setCompleted] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [serverState, setServerState] = useState("connecting");
  const [connectionAttempt, setConnectionAttempt] = useState(0);
  const pollTimer = useRef(null);
  const typeTimer = useRef(null);
  const pollGeneration = useRef(0);
  const pollFailures = useRef(0);
  const lastReadyAt = useRef(0);

  async function loadCalls(signal) {
    const requestSignal = signal
      ? AbortSignal.any([signal, AbortSignal.timeout(25000)])
      : AbortSignal.timeout(25000);
    const response = await fetch(`${API_BASE_URL}/call`, { signal: requestSignal });
    if (!response.ok) throw new Error("Could not reach the service. It may be waking up.");
    const data = await response.json();
    if (!signal?.aborted) setCalls(data.calls ?? []);
  }
  function stopPolling() {
    pollGeneration.current += 1;
    pollFailures.current = 0;
    window.clearTimeout(pollTimer.current);
    pollTimer.current = null;
    setPolling(false);
  }
  useEffect(() => {
    let cancelled = false;
    let retryTimer;
    let failures = 0;
    const controller = new AbortController();
    async function connect() {
      try {
        await loadCalls(controller.signal);
        if (!cancelled) { lastReadyAt.current = Date.now(); setServerState("ready"); }
      } catch {
        if (cancelled) return;
        setServerState((current) => current === "ready" ? "ready" : failures >= 6 ? "unavailable" : "waking");
        retryTimer = window.setTimeout(connect, Math.min(2000 * 2 ** failures++, 15000));
      }
    }
    connect();
    return () => { cancelled = true; controller.abort(); window.clearTimeout(retryTimer); };
  }, [connectionAttempt]);
  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState === "visible" && Date.now() - lastReadyAt.current > 5 * 60 * 1000) retryConnection();
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => document.removeEventListener("visibilitychange", onVisible);
  }, []);
  useEffect(() => () => { window.clearTimeout(pollTimer.current); window.clearTimeout(typeTimer.current); }, []);
  useEffect(() => {
    window.clearTimeout(typeTimer.current);
    const text = summary?.summaryText ?? "";
    setDisplayedSummary("");
    if (!text) return undefined;
    // Only a newly generated summary types in. Existing summaries stay fully visible.
    if (!completed || window.matchMedia("(prefers-reduced-motion: reduce)").matches) { setDisplayedSummary(text); return undefined; }
    let index = 0;
    const next = () => { index += 1; setDisplayedSummary(text.slice(0, index)); if (index < text.length) typeTimer.current = window.setTimeout(next, 9); };
    typeTimer.current = window.setTimeout(next, completed ? 700 : 0);
    return () => window.clearTimeout(typeTimer.current);
  }, [summary?.summaryText, completed]);

  async function getSummary(callId) {
    const response = await fetch(`${API_BASE_URL}/call/${callId}/summary`, { signal: AbortSignal.timeout(25000) });
    if (response.status === 409) return null;
    if (!response.ok) throw new Error("Could not load the generated summary");
    return response.json();
  }
  async function pollCall(callId, generation) {
    if (generation !== pollGeneration.current) return;
    try {
      const response = await fetch(`${API_BASE_URL}/call/${callId}`, { signal: AbortSignal.timeout(25000) });
      if (!response.ok) {
        const error = new Error("Could not load the recording status");
        error.permanent = [400, 401, 403, 404].includes(response.status);
        throw error;
      }
      const call = await response.json();
      if (generation !== pollGeneration.current) return;
      setSelectedCall(call);
      lastReadyAt.current = Date.now();
      setServerState("ready");
      if (call.status === "READY") {
        const data = await getSummary(callId);
        if (generation !== pollGeneration.current) return;
        if (data) { setSummary(data); setCompleted(true); setMessage("Summary ready"); loadCalls().catch(retryConnection); stopPolling(); return; }
      }
      if (call.status === "FAILED") { setMessage("Processing failed. Check the backend job record for details."); loadCalls().catch(retryConnection); stopPolling(); return; }
      pollFailures.current = 0;
      setMessage(({ UPLOADED:"Preparing your audio…", TRANSCRIBING:"Transcribing your recording…", SUMMARIZING:"Generating your call summary…" })[call.status] ?? "Processing your recording…");
      pollTimer.current = window.setTimeout(() => pollCall(callId, generation), 2500);
    } catch (error) {
      if (generation !== pollGeneration.current) return;
      if (error.permanent || ++pollFailures.current > 10) {
        setMessage(error.permanent ? error.message : "Still unable to reach the server. Select this call to try again.");
        stopPolling();
        return;
      }
      setMessage("Connection interrupted. Reconnecting to the server…");
      setServerState("waking");
      if (pollFailures.current === 1) setConnectionAttempt((attempt) => attempt + 1);
      const delay = Math.min(3000 * 2 ** (pollFailures.current - 1), 15000);
      pollTimer.current = window.setTimeout(() => pollCall(callId, generation), delay);
    }
  }
  function startPolling(callId) {
    stopPolling();
    setPolling(true);
    pollCall(callId, pollGeneration.current);
  }
  function retryConnection() {
    setServerState("connecting");
    setConnectionAttempt((attempt) => attempt + 1);
  }
  async function upload(event) {
    event.preventDefault(); if (!file || serverState !== "ready") return;
    stopPolling(); setCompleted(false); setBusy(true); setSummary(null); setDisplayedSummary(""); setSelectedCall({ status:"UPLOADING" }); setMessage("Uploading your recording…");
    try {
      const formData = new FormData(); formData.append("file", file);
      const response = await fetch(`${API_BASE_URL}/call`, { method:"POST", body:formData });
      const call = await response.json();
      if (!response.ok) {
        const error = new Error(call.error ?? "Upload failed");
        error.permanent = response.status < 500;
        throw error;
      }
      setSelectedCall(call); lastReadyAt.current = Date.now(); setServerState("ready"); setMessage("Upload complete. Starting transcription…");
      loadCalls().catch(retryConnection);
      startPolling(call.callId);
    } catch (error) {
      setSelectedCall(null);
      if (error.permanent) setMessage(error.message);
      else {
        setMessage("Upload could not be confirmed. Reconnecting—check your calls before trying again.");
        setServerState("waking");
        setConnectionAttempt((attempt) => attempt + 1);
      }
    } finally { setBusy(false); }
  }
  function chooseCall(call) {
    stopPolling(); setCompleted(false); setSelectedCall(call); setSummary(null);
    if (call.status === "READY") { setMessage("Loading summary…"); getSummary(call.callId).then((data) => { setSummary(data); setMessage(data ? "Summary loaded" : "Summary is being prepared…"); }).catch((error) => setMessage(error.message)); return; }
    startPolling(call.callId);
  }

  function selectFile(nextFile) {
    setDragging(false);
    if (!nextFile) return;
    setCompleted(false);
    if (!/\.(wav|mp3|m4a|mp4|ogg)$/i.test(nextFile.name) || !nextFile.size || nextFile.size > 20 * 1024 * 1024) {
      setFile(null);
      setMessage("Choose a nonempty WAV, MP3, M4A, MP4, or OGG file up to 20 MB.");
      return;
    }
    setFile(nextFile);
    setMessage("Ready to upload your recording.");
    if (serverState === "ready" && Date.now() - lastReadyAt.current > 5 * 60 * 1000) retryConnection();
  }

  return <main>
    <section className="hero">
      <h1>Turn your calls into <span>clarity</span></h1>
      <form className={`input-bar${dragging ? " dragging" : ""}`} onSubmit={upload}
        onDragEnter={(event) => { event.preventDefault(); setDragging(true); }}
        onDragOver={(event) => event.preventDefault()}
        onDragLeave={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setDragging(false); }}
        onDrop={(event) => { event.preventDefault(); selectFile(event.dataTransfer.files[0]); }}>
        <label>
          <input type="file" accept=".wav,.mp3,.m4a,.mp4,.ogg,audio/*" onChange={(event) => selectFile(event.target.files[0])} />
          <span className="upload-glyph"><AudioGlyph /></span>
          <span className="upload-copy"><strong>{file ? file.name : "Drop an audio file here"}</strong><small>{file ? `${sizeLabel(file.size)} · Ready to upload` : "MP3, WAV, M4A, MP4, OGG · up to 20 MB"}</small></span>
        </label>
        <button type="submit" aria-label="Upload and summarize recording" title="Upload and summarize" disabled={!file || busy || serverState !== "ready"}>
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 20V4m0 0-6 6m6-6 6 6" /></svg>
        </button>
      </form>
      {serverState !== "ready" && <div className="connection-notice" role="status">
        <span className="connection-spinner" aria-hidden="true" />
        <span>{serverState === "connecting" ? "Connecting to the server…" : serverState === "waking" ? "The server may be waking up. This can take about a minute; we'll keep trying." : "Still unable to connect. We'll keep trying; check Render logs if this continues."}</span>
        <button type="button" onClick={retryConnection}>Retry now</button>
      </div>}
      <ProcessingOrb message={message} active={busy || polling} completed={completed} />
    </section>
    {summary && <section className={`output${completed ? " completed" : ""}`}><p className="label">GENERATED SUMMARY</p><p className="typewriter">{displayedSummary}<span className={displayedSummary.length < summary.summaryText.length ? "cursor" : "cursor quiet"}>|</span></p><details><summary>Transcript</summary><p>{summary.transcript}</p></details></section>}
    <section className="previous">
      <div className="previous-heading"><p>Previous audio calls</p><button onClick={retryConnection}>Refresh</button></div>
      {calls.length === 0 ? <p className="empty-list">Your recordings will appear here.</p> :
        <div className="call-list">{calls.map((call) => <button className={selectedCall?.callId === call.callId ? "selected" : ""} onClick={() => chooseCall(call)} key={call.callId}>
          <span className="call-glyph"><AudioGlyph /></span>
          <span className="call-meta"><strong>Call recording</strong><small>{new Date(call.createdAt).toLocaleString()} · {sizeLabel(call.sizeBytes)} · {call.contentType.replace("audio/", "").replace("video/", "").toUpperCase()}</small></span>
          {call.status !== "READY" && <em className={call.status.toLowerCase()}>{call.status}</em>}<span className="row-arrow" aria-hidden="true">›</span>
        </button>)}</div>}
    </section>
  </main>;
}
