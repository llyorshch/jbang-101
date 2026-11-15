import { createElement, useCallback, useEffect, useRef, useState } from 'react';
import htm from 'htm';
import { getClient, listClientCases, startOffboarding, startOnboarding } from '../api.js';
import { StatusBadge } from './ClientList.js';
import { CasePanel, CaseStateBadge, formatDateTime } from './CasePanel.js';

const html = htm.bind(createElement);

const ONBOARDING_ALLOWED = new Set(['PROSPECT', 'REJECTED', 'OFFBOARDED']);

// A client that has been through onboarding before (rejected or offboarded) is re-onboarded.
export const onboardingLabel = (status) => (status === 'PROSPECT' ? 'Start onboarding' : 'Re-onboard');
const OFFBOARDING_ALLOWED = new Set(['ACTIVE']);

export function allowedStarts(client, cases) {
  const hasActiveCase = Array.isArray(cases) && cases.some((c) => c.state === 'ACTIVE');
  return {
    onboarding: !!client && !hasActiveCase && ONBOARDING_ALLOWED.has(client.status),
    offboarding: !!client && !hasActiveCase && OFFBOARDING_ALLOWED.has(client.status),
  };
}

const TYPE_LABELS = { ONBOARDING: 'Onboarding', OFFBOARDING: 'Offboarding' };

export function ClientDetail({ clientId }) {
  const [client, setClient] = useState(null);
  const [cases, setCases] = useState([]);
  const [loadError, setLoadError] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [starting, setStarting] = useState(false);
  const [startError, setStartError] = useState(null);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  // Reloads the client and its cases together; used on mount and after every case change.
  const load = useCallback(
    async (signal) => {
      try {
        const [clientData, caseData] = await Promise.all([
          getClient(clientId, { signal }),
          listClientCases(clientId, { signal }),
        ]);
        if (!mounted.current) return;
        setClient(clientData);
        setCases(Array.isArray(caseData) ? caseData : []);
        setLoadError(null);
        setNotFound(false);
      } catch (err) {
        if (err?.name === 'AbortError' || !mounted.current) return;
        if (err.status === 404) setNotFound(true);
        else setLoadError(err.message || 'Could not load the client.');
      }
    },
    [clientId],
  );

  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal);
    return () => controller.abort();
  }, [load]);

  const start = async (kind) => {
    setStarting(true);
    setStartError(null);
    try {
      await (kind === 'onboarding' ? startOnboarding(clientId) : startOffboarding(clientId));
      await load();
    } catch (err) {
      if (mounted.current) setStartError(err.message || 'Could not start the case.');
    } finally {
      if (mounted.current) setStarting(false);
    }
  };

  if (notFound) {
    return html`
      <section className="card">
        <h1>Client not found</h1>
        <p><a href="#/">Back to clients</a></p>
      </section>
    `;
  }

  if (!client) {
    return html`
      <section className="stack">
        <p><a href="#/">← Clients</a></p>
        ${loadError
          ? html`<div className="alert alert-error" role="alert">${loadError}</div>`
          : html`<p className="muted" role="status">Loading client…</p>`}
      </section>
    `;
  }

  const activeCase = cases.find((c) => c.state === 'ACTIVE') ?? null;
  const pastCases = cases.filter((c) => c !== activeCase);
  const allowed = allowedStarts(client, cases);

  return html`
    <section className="stack">
      <p><a href="#/">← Clients</a></p>

      ${loadError && html`<div className="alert alert-error" role="alert">${loadError}</div>`}

      <div className="card">
        <div className="page-head">
          <h1>${client.name}</h1>
          <${StatusBadge} status=${client.status} />
        </div>
        <dl className="details">
          <div><dt>Email</dt><dd>${client.email}</dd></div>
          <div><dt>Country</dt><dd>${client.country}</dd></div>
          <div><dt>Created</dt><dd>${formatDateTime(client.createdAt)}</dd></div>
          <div><dt>Id</dt><dd><code>${client.id}</code></dd></div>
        </dl>
        ${(allowed.onboarding || allowed.offboarding) &&
        html`<div className="button-row">
          ${allowed.onboarding &&
          html`<button type="button" className="btn btn-primary" disabled=${starting} onClick=${() => start('onboarding')}>
            ${starting ? 'Starting…' : onboardingLabel(client.status)}
          </button>`}
          ${allowed.offboarding &&
          html`<button type="button" className="btn btn-primary" disabled=${starting} onClick=${() => start('offboarding')}>
            ${starting ? 'Starting…' : 'Start offboarding'}
          </button>`}
        </div>`}
        ${startError && html`<div className="alert alert-error" role="alert">${startError}</div>`}
      </div>

      <section className="card">
        <h2>Active case</h2>
        ${activeCase
          ? html`<${CasePanel} key=${activeCase.id} caseView=${activeCase} onChanged=${() => load()} />`
          : html`<p className="muted">No active case.</p>`}
      </section>

      <section className="card">
        <h2>Past cases</h2>
        ${pastCases.length === 0
          ? html`<p className="muted">No past cases.</p>`
          : html`<ul className="past-cases">
              ${pastCases.map(
                (c) => html`<li key=${c.id}>
                  <details>
                    <summary>
                      <span>${TYPE_LABELS[c.type] ?? 'Case'}</span>
                      <${CaseStateBadge} state=${c.state} />
                      <span className="muted">
                        ${formatDateTime(c.startedAt)}${c.endedAt ? ` – ${formatDateTime(c.endedAt)}` : ''}
                      </span>
                    </summary>
                    <${CasePanel} caseView=${c} readOnly=${true} />
                  </details>
                </li>`,
              )}
            </ul>`}
      </section>
    </section>
  `;
}
