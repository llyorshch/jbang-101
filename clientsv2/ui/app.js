import { Component, StrictMode, createElement, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import htm from 'htm';
import { ClientList } from './components/ClientList.js';
import { ClientDetail } from './components/ClientDetail.js';

const html = htm.bind(createElement);

// Ids are only accepted in a conservative shape; they are still encoded when sent to the API.
const CLIENT_ROUTE = /^#\/clients\/([A-Za-z0-9_-]{1,100})\/?$/;

export function parseRoute(hash) {
  if (hash === '' || hash === '#' || hash === '#/') return { name: 'list' };
  const match = CLIENT_ROUTE.exec(hash);
  if (match) return { name: 'client', clientId: match[1] };
  return { name: 'notFound' };
}

function useHashRoute() {
  const [route, setRoute] = useState(() => parseRoute(window.location.hash));
  useEffect(() => {
    const onChange = () => setRoute(parseRoute(window.location.hash));
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return route;
}

class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error) {
    console.error('Unexpected UI error', error);
  }

  render() {
    if (this.state.error) {
      return html`
        <div className="alert alert-error" role="alert">
          <p><strong>Something went wrong.</strong> Reload the page to try again.</p>
          <p><a href="#/" onClick=${() => this.setState({ error: null })}>Back to clients</a></p>
        </div>
      `;
    }
    return this.props.children;
  }
}

function NotFound() {
  return html`
    <section className="card">
      <h1>Page not found</h1>
      <p><a href="#/">Back to clients</a></p>
    </section>
  `;
}

function App() {
  const route = useHashRoute();

  useEffect(() => {
    document.title = route.name === 'client' ? 'Client · Onboarding Console' : 'Clients · Onboarding Console';
  }, [route]);

  let page;
  if (route.name === 'list') page = html`<${ClientList} />`;
  else if (route.name === 'client') page = html`<${ClientDetail} key=${route.clientId} clientId=${route.clientId} />`;
  else page = html`<${NotFound} />`;

  return html`
    <header className="app-header">
      <a className="brand" href="#/">Onboarding Console</a>
      <span className="muted">Client onboarding & offboarding</span>
    </header>
    <main className="app-main">
      <${ErrorBoundary} key=${route.name + (route.clientId ?? '')}>${page}<//>
    </main>
  `;
}

const container = document.getElementById('root');
if (container) {
  createRoot(container).render(html`<${StrictMode}><${App} /><//>`);
}
