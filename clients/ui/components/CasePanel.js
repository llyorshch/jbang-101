import { createElement, useEffect, useId, useRef, useState } from 'react';
import htm from 'htm';
import { caseDiagramUrl, performCaseAction } from '../api.js';

const html = htm.bind(createElement);

export const NOTE_MAX_LENGTH = 500;

const STAGES = {
  ONBOARDING: ['DRAFT', 'OPEN', 'FINISHED'],
  OFFBOARDING: ['OPENED', 'CHECKS_ONGOING', 'FINISHED'],
};

const TYPE_LABELS = { ONBOARDING: 'Onboarding', OFFBOARDING: 'Offboarding' };

const STATE_BADGES = {
  ACTIVE: { label: 'In progress', tone: 'info' },
  SUCCEEDED: { label: 'Succeeded', tone: 'success' },
  FAILED: { label: 'Failed', tone: 'danger' },
  COMPLETED: { label: 'Completed', tone: 'neutral' },
};

// One entry per button. `decide` expands into two buttons with an outcome.
const ACTION_BUTTONS = {
  submit: [{ label: 'Submit draft', tone: 'primary' }],
  'request-documents': [{ label: 'Request documents' }],
  'confirm-documents': [{ label: 'Documents received', tone: 'primary' }],
  'request-amendment': [{ label: 'Request amendment' }],
  'confirm-amendment': [{ label: 'Amendment received', tone: 'primary' }],
  decide: [
    { label: 'Approve', tone: 'success', outcome: 'APPROVED' },
    { label: 'Reject', tone: 'danger', outcome: 'REJECTED' },
  ],
  'start-checks': [{ label: 'Start checks', tone: 'primary' }],
  'complete-checks': [{ label: 'Complete checks', tone: 'primary' }],
};

const humanize = (value) => {
  const text = String(value ?? '').replace(/[_-]+/g, ' ').trim().toLowerCase();
  return text ? text.charAt(0).toUpperCase() + text.slice(1) : '';
};

export function actionLabel(action, outcome) {
  const buttons = ACTION_BUTTONS[action];
  if (!buttons) return humanize(action);
  if (outcome) return buttons.find((b) => b.outcome === outcome)?.label ?? humanize(action);
  return buttons[0].label;
}

const dateTimeFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' });

export function formatDateTime(value) {
  if (!value) return '';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : dateTimeFormat.format(date);
}

export function CaseStateBadge({ state }) {
  const badge = STATE_BADGES[state] ?? { label: humanize(state) || 'Unknown', tone: 'neutral' };
  return html`<span className=${`badge badge-${badge.tone}`}>${badge.label}</span>`;
}

function StageStepper({ type, stage, state }) {
  const stages = STAGES[type] ?? [];
  const currentIndex = stages.indexOf(stage);
  const finished = state && state !== 'ACTIVE';
  return html`
    <ol className="stepper" aria-label=${`${TYPE_LABELS[type] ?? 'Case'} stages`}>
      ${stages.map((name, index) => {
        let status = 'todo';
        if (index < currentIndex || (finished && index === currentIndex)) status = 'done';
        if (index === currentIndex) status = finished ? 'done current' : 'current';
        return html`<li
          key=${name}
          className=${`step step-${status.replace(' ', ' step-')}`}
          aria-current=${index === currentIndex ? 'step' : undefined}
        >
          <span className="step-dot" aria-hidden="true">${index + 1}</span>
          <span className="step-label">${humanize(name)}</span>
        </li>`;
      })}
    </ol>
  `;
}

function CaseHistory({ history }) {
  if (!Array.isArray(history) || history.length === 0) {
    return html`<p className="muted">No steps completed yet.</p>`;
  }
  return html`
    <ol className="history">
      ${history.map(
        (entry, index) => html`<li key=${`${entry.completedAt ?? ''}-${index}`} className="history-item">
          <div className="history-head">
            <strong>${humanize(entry.step) || actionLabel(entry.action, entry.outcome)}</strong>
            ${entry.action && html`<span className="muted"> · ${actionLabel(entry.action, entry.outcome)}</span>`}
            ${entry.outcome &&
            html` <span className=${`badge badge-${entry.outcome === 'APPROVED' ? 'success' : entry.outcome === 'REJECTED' ? 'danger' : 'neutral'}`}
              >${humanize(entry.outcome)}</span
            >`}
            <time className="muted history-time" dateTime=${entry.completedAt ?? undefined}>
              ${formatDateTime(entry.completedAt)}
            </time>
          </div>
          ${entry.note && html`<p className="history-note">${entry.note}</p>`}
        </li>`,
      )}
    </ol>
  `;
}

/**
 * Modal with the case's CMMN diagram (PNG rendered by the backend). Uses a native <dialog>:
 * Esc and the close button close it natively; a click on the backdrop closes it too.
 * `version` is the cache buster (null while closed); the parent sets a fresh one on every
 * open and whenever the case changes while open, so the image always shows the current state.
 */
function DiagramDialog({ caseView, version, onClose }) {
  const dialogRef = useRef(null);
  // Load status is tracked per src, so a late event from a previous image can't affect the current one.
  const [load, setLoad] = useState({ src: null, status: 'loading' });
  const titleId = useId();
  const open = version !== null;

  // Open/close the native dialog in sync with state.
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  const typeLabel = TYPE_LABELS[caseView.type] ?? 'Case';
  const src = open ? caseDiagramUrl(caseView, version) : null;
  const status = load.src === src ? load.status : 'loading';

  const onClick = (event) => {
    // The inner wrapper fills the dialog, so a click whose target is the dialog itself is on the backdrop.
    if (event.target === dialogRef.current) onClose();
  };

  return html`
    <dialog ref=${dialogRef} className="diagram-dialog" aria-labelledby=${titleId} onClose=${onClose} onClick=${onClick}>
      <div className="diagram-inner">
        <header className="diagram-head">
          <h3 id=${titleId}>${typeLabel} case diagram</h3>
          <button type="button" className="btn" onClick=${onClose}>Close</button>
        </header>
        <div className="diagram-body">
          ${open &&
          (!src || status === 'error'
            ? html`<div className="alert alert-error" role="alert">Diagram not available</div>`
            : html`
                ${status === 'loading' && html`<p className="muted" role="status">Loading diagram…</p>`}
                <img
                  key=${src}
                  src=${src}
                  alt=${`${typeLabel} case diagram`}
                  className=${status === 'loading' ? 'diagram-img is-loading' : 'diagram-img'}
                  onLoad=${() => setLoad({ src, status: 'loaded' })}
                  onError=${() => setLoad({ src, status: 'error' })}
                />
              `)}
        </div>
      </div>
    </dialog>
  `;
}

/**
 * Shows a case: stage stepper, badges, action buttons with an optional note, and history.
 * `onChanged(updatedCase)` is awaited after each successful action so the parent can
 * refresh the case and the client (whose status changes too).
 */
export function CasePanel({ caseView, readOnly = false, onChanged }) {
  const baseId = useId();
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [diagramVersion, setDiagramVersion] = useState(null);
  const mounted = useRef(true);

  // If the case changes while the diagram is open (e.g. after an action), fetch a fresh image.
  useEffect(() => {
    setDiagramVersion((current) => (current === null ? null : Date.now()));
  }, [caseView]);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  if (!caseView) return null;

  const actions = Array.isArray(caseView.availableActions) ? caseView.availableActions : [];
  const showActions = !readOnly && caseView.state === 'ACTIVE' && actions.length > 0;

  const run = async (action, outcome) => {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const updated = await performCaseAction(caseView, action, { note, outcome });
      if (mounted.current) setNote('');
      await onChanged?.(updated);
    } catch (err) {
      if (mounted.current) setError(err.message || 'The action failed.');
    } finally {
      if (mounted.current) setBusy(false);
    }
  };

  const noteId = `${baseId}-note`;

  return html`
    <article className="case-panel" aria-busy=${busy ? 'true' : undefined}>
      <header className="case-head">
        <h3>${TYPE_LABELS[caseView.type] ?? 'Case'}</h3>
        <div className="badges">
          ${caseView.subStage && html`<span className="badge badge-warning">${humanize(caseView.subStage)}</span>`}
          <${CaseStateBadge} state=${caseView.state} />
          <button type="button" className="btn btn-small" onClick=${() => setDiagramVersion(Date.now())}>View diagram</button>
        </div>
      </header>

      <p className="muted case-meta">
        Started ${formatDateTime(caseView.startedAt)}
        ${caseView.endedAt && ` · Ended ${formatDateTime(caseView.endedAt)}`}
      </p>

      <${StageStepper} type=${caseView.type} stage=${caseView.stage} state=${caseView.state} />

      ${showActions &&
      html`<div className="case-actions">
        <div className="field">
          <label htmlFor=${noteId}>Note <span className="muted">(optional)</span></label>
          <textarea
            id=${noteId}
            rows=${3}
            maxLength=${NOTE_MAX_LENGTH}
            value=${note}
            disabled=${busy}
            onChange=${(event) => setNote(event.target.value)}
            aria-describedby=${`${noteId}-count`}
          ></textarea>
          <small id=${`${noteId}-count`} className="muted">${note.length}/${NOTE_MAX_LENGTH}</small>
        </div>
        ${error && html`<div className="alert alert-error" role="alert">${error}</div>`}
        <div className="button-row">
          ${actions.flatMap((action) =>
            (ACTION_BUTTONS[action] ?? [{ label: humanize(action) }]).map(
              (button) => html`<button
                key=${`${action}-${button.outcome ?? ''}`}
                type="button"
                className=${`btn${button.tone ? ` btn-${button.tone}` : ''}`}
                disabled=${busy}
                onClick=${() => run(action, button.outcome)}
              >
                ${button.label}
              </button>`,
            ),
          )}
        </div>
        ${busy && html`<p className="muted" role="status">Working…</p>`}
      </div>`}

      <section className="case-history" aria-label="History">
        <h4>History</h4>
        <${CaseHistory} history=${caseView.history} />
      </section>

      <${DiagramDialog} caseView=${caseView} version=${diagramVersion} onClose=${() => setDiagramVersion(null)} />
    </article>
  `;
}
