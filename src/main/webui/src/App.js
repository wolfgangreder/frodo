import React, { useState, useEffect } from 'react';
import './App.css';

function App() {
  const [appInfo, setAppInfo] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetch('/api/info')
      .then(res => res.json())
      .then(data => setAppInfo(data))
      .catch(err => setError(err.message));
  }, []);

  return (
    <div className="App">
      <header className="App-header">
        <h1>🧙 Frodo</h1>
        <p className="subtitle">Quarkus Server Dashboard</p>
      </header>

      <main className="App-main">
        <section className="card">
          <h2>Application Info</h2>
          {error && <p className="error">Error: {error}</p>}
          {appInfo ? (
            <dl>
              <dt>Name</dt>
              <dd>{appInfo.name}</dd>
              <dt>Version</dt>
              <dd>{appInfo.version}</dd>
              <dt>Description</dt>
              <dd>{appInfo.description}</dd>
            </dl>
          ) : (
            !error && <p>Loading…</p>
          )}
        </section>

        <section className="card">
          <h2>Quick Links</h2>
          <ul className="link-list">
            <li>
              <a href="/swagger-ui" target="_blank" rel="noreferrer">
                📖 Swagger UI (REST API)
              </a>
            </li>
            <li>
              <a href="/q/metrics" target="_blank" rel="noreferrer">
                📊 Prometheus Metrics
              </a>
            </li>
            <li>
              <a href="/q/health" target="_blank" rel="noreferrer">
                💚 Health Check
              </a>
            </li>
            <li>
              <a href="/q/openapi" target="_blank" rel="noreferrer">
                📄 OpenAPI Spec
              </a>
            </li>
          </ul>
        </section>
      </main>
    </div>
  );
}

export default App;
