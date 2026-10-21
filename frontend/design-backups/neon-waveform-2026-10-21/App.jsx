import { useEffect, useRef, useState } from "react";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";
// Positions and heights traced from the chosen waveform reference image.
const WAVE_BARS = [
  [506, 5], [518, 9], [530, 16], [542, 24], [553, 15], [565, 7],
  [600, 6], [613, 15], [625, 31], [636, 51], [648, 31], [660, 12], [671, 5],
  [684, 7], [696, 13], [708, 23], [720, 36], [731, 59], [743, 84], [755, 102],
  [768, 83], [778, 52], [790, 28], [802, 15], [813, 8], [822, 14],
  [835, 28], [847, 57], [857, 84], [869, 62], [881, 31], [893, 14], [905, 7],
  [918, 5], [931, 9], [944, 16], [954, 33], [966, 58], [979, 79],
  [989, 57], [1001, 34], [1013, 16], [1026, 7], [1038, 4],
  [1075, 5], [1087, 11], [1099, 18], [1111, 26], [1123, 17], [1135, 11], [1147, 5],
].map(([x, height]) => ({ x, height }));
const sizeLabel = (bytes) => bytes < 1024 * 1024 ? `${Math.round(bytes / 1024)} KB` : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;

function ProcessingWaveform({ message }) {
  const bars = (kind) => WAVE_BARS.map(({ x, height }, index) =>
    <line className={`wave-bar ${kind}`} key={`${kind}-${x}`}
      style={{ "--height-delay": `${-((index * 0.23) % 1.5)}s` }}
      x1={x} x2={x} y1={412 - height / 2} y2={412 + height / 2} />);

  return <div className="generating">
    <div className="waveform">
      <svg viewBox="330 350 1000 170" role="img" aria-label="Animated audio waveform">
        <defs>
          <linearGradient id="wave-spectrum" gradientUnits="userSpaceOnUse" x1="350" y1="0" x2="1320" y2="0">
            <stop offset="0" stopColor="#ff4acc" />
            <stop offset=".30" stopColor="#ff4acc" />
            <stop offset=".44" stopColor="#ab68ff" />
            <stop offset=".56" stopColor="#7688ff" />
            <stop offset=".69" stopColor="#35c7ff" />
            <stop offset="1" stopColor="#28e0ff" />
          </linearGradient>
          <filter id="wave-bloom" x="-50%" y="-100%" width="200%" height="300%">
            <feGaussianBlur stdDeviation="3.5" />
          </filter>
          <filter id="wave-reflection" x="-50%" y="-500%" width="200%" height="1100%">
            <feGaussianBlur stdDeviation="7" />
          </filter>
          <clipPath id="wave-window"><rect x="355" y="350" width="960" height="170" /></clipPath>
        </defs>
        <line className="wave-baseline" x1="355" y1="412" x2="1315" y2="412" />
        <g clipPath="url(#wave-window)">
          <g className="wave-stream">
            {[-800, 0, 800].map((offset) => <g key={offset} transform={`translate(${offset} 0)`}>
              <g className="wave-reflection" filter="url(#wave-reflection)">
                <ellipse cx="535" cy="494" rx="52" ry="3" fill="#ff42cb" />
                <ellipse cx="635" cy="494" rx="43" ry="3" fill="#ee48e9" />
                <ellipse cx="755" cy="494" rx="62" ry="4" fill="#af65ff" />
                <ellipse cx="857" cy="494" rx="48" ry="4" fill="#6d86ff" />
                <ellipse cx="979" cy="494" rx="57" ry="4" fill="#2acfff" />
                <ellipse cx="1112" cy="494" rx="35" ry="3" fill="#24dfff" />
              </g>
              <g filter="url(#wave-bloom)">{bars("wave-light")}</g>
              <g>{bars("wave-core")}</g>
            </g>)}
          </g>
        </g>
      </svg>
    </div>
    <div className="generating-message"><span className="neon-orb" /><span>{message}</span><b>•••</b></div>
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
  const pollTimer = useRef(null);
  const typeTimer = useRef(null);

  async function loadCalls() {
    const response = await fetch(`${API_BASE_URL}/call`);
    if (!response.ok) throw new Error("Could not reach the service. It may be waking up.");
    setCalls((await response.json()).calls ?? []);
  }
  function stopPolling() { window.clearTimeout(pollTimer.current); pollTimer.current = null; setPolling(false); }
  useEffect(() => { loadCalls().catch((error) => setMessage(error.message)); return () => { window.clearTimeout(pollTimer.current); window.clearTimeout(typeTimer.current); }; }, []);
  useEffect(() => {
    window.clearTimeout(typeTimer.current);
    const text = summary?.summaryText ?? "";
    setDisplayedSummary("");
    if (!text) return undefined;
    let index = 0;
    const next = () => { index += 1; setDisplayedSummary(text.slice(0, index)); if (index < text.length) typeTimer.current = window.setTimeout(next, 9); };
    next();
    return () => window.clearTimeout(typeTimer.current);
  }, [summary?.summaryText]);

  async function getSummary(callId) {
    const response = await fetch(`${API_BASE_URL}/call/${callId}/summary`);
    if (response.status === 409) return null;
    if (!response.ok) throw new Error("Could not load the generated summary");
    return response.json();
  }
  async function pollCall(callId) {
    const response = await fetch(`${API_BASE_URL}/call/${callId}`);
    if (!response.ok) throw new Error("Could not load the recording status");
    const call = await response.json();
    setSelectedCall(call); await loadCalls();
    if (call.status === "READY") { const data = await getSummary(callId); if (data) { setSummary(data); setMessage("Summary generated"); stopPolling(); return; } }
    if (call.status === "FAILED") { setMessage("Processing failed. Check the backend job record for details."); stopPolling(); return; }
    setMessage(({ UPLOADED:"Preparing your audio…", TRANSCRIBING:"Transcribing your recording…", SUMMARIZING:"Generating your call summary…" })[call.status] ?? "Processing your recording…");
    setPolling(true);
    pollTimer.current = window.setTimeout(() => pollCall(callId).catch((error) => { setMessage(error.message); stopPolling(); }), 2500);
  }
  async function upload(event) {
    event.preventDefault(); if (!file) return;
    stopPolling(); setBusy(true); setSummary(null); setDisplayedSummary(""); setSelectedCall({ status:"UPLOADING" }); setMessage("Uploading your recording…");
    try {
      const formData = new FormData(); formData.append("file", file);
      const response = await fetch(`${API_BASE_URL}/call`, { method:"POST", body:formData });
      const call = await response.json(); if (!response.ok) throw new Error(call.error ?? "Upload failed");
      setSelectedCall(call); setPolling(true); setMessage("Upload complete. Starting transcription…");
      pollCall(call.callId).catch((error) => { setMessage(error.message); stopPolling(); });
    } catch (error) { setSelectedCall(null); setMessage(error.message); } finally { setBusy(false); }
  }
  function chooseCall(call) {
    stopPolling(); setSelectedCall(call); setSummary(null);
    if (call.status === "READY") { setMessage("Loading summary…"); getSummary(call.callId).then((data) => { setSummary(data); setMessage(data ? "Summary generated" : "Summary is being prepared…"); }).catch((error) => setMessage(error.message)); return; }
    setPolling(true); pollCall(call.callId).catch((error) => { setMessage(error.message); stopPolling(); });
  }

  return <main>
    <section className="hero">
      <p className="brand">CALL SUMMARY</p><span className="brand-line" />
      <form className="input-bar" onSubmit={upload}>
        <label><input type="file" accept="audio/wav,audio/x-wav,audio/mpeg,audio/mp4,audio/m4a,audio/x-m4a,video/mp4,audio/ogg" onChange={(event) => setFile(event.target.files[0] ?? null)} /><span className="plus">+</span><span className={file ? "filename" : "file-prompt"}>{file ? file.name : "Select audio recording"}</span>{file && <small>{sizeLabel(file.size)}</small>}</label>
        <button type="submit" disabled={!file || busy}>{busy ? "Uploading" : "Generate"}<span>↗</span></button>
      </form>
      <p className="formats">WAV · MP3 · M4A · MP4 · OGG &nbsp; / &nbsp; MAX 20 MB</p>
      {(busy || polling) && <ProcessingWaveform message={message} />}
    </section>
    {summary && <section className="output"><p className="label">GENERATED SUMMARY</p><p className="typewriter">{displayedSummary}<span className={displayedSummary.length < summary.summaryText.length ? "cursor" : "cursor quiet"}>|</span></p><details><summary>Transcript</summary><p>{summary.transcript}</p></details></section>}
    {calls.length > 0 && <section className="previous"><div className="previous-heading"><p className="label">PREVIOUS AUDIO CALLS</p><button onClick={() => loadCalls().catch((error) => setMessage(error.message))}>Refresh</button></div><div className="call-list">{calls.map((call) => <button className={selectedCall?.callId === call.callId ? "selected" : ""} onClick={() => chooseCall(call)} key={call.callId}><span className="audio-mark">◉</span><span><strong>{call.contentType.replace("audio/", "").replace("video/", "")}</strong><small>{sizeLabel(call.sizeBytes)} · {new Date(call.createdAt).toLocaleDateString()}</small></span><em className={call.status.toLowerCase()}>{call.status}</em></button>)}</div></section>}
  </main>;
}
