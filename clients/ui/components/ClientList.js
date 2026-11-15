import { createElement, useCallback, useEffect, useId, useState } from 'react';
import htm from 'htm';
import { createClient, deleteClient, listClients } from '../api.js';

const html = htm.bind(createElement);

const STATUS_LABELS = {
  PROSPECT: 'Prospect',
  ONBOARDING: 'Onboarding',
  ACTIVE: 'Active',
  REJECTED: 'Rejected',
  OFFBOARDING: 'Offboarding',
  OFFBOARDED: 'Offboarded',
};

const STATUS_TONES = {
  PROSPECT: 'neutral',
  ONBOARDING: 'info',
  ACTIVE: 'success',
  REJECTED: 'danger',
  OFFBOARDING: 'warning',
  OFFBOARDED: 'neutral',
};

export function StatusBadge({ status }) {
  const label = STATUS_LABELS[status] ?? String(status ?? 'Unknown');
  const tone = STATUS_TONES[status] ?? 'neutral';
  return html`<span className=${`badge badge-${tone}`}>${label}</span>`;
}

export const clientHref = (id) => `#/clients/${encodeURIComponent(String(id))}`;

// Mirrors the backend rule: a client can't be deleted while one of its cases is in progress.
const CASE_IN_PROGRESS = new Set(['ONBOARDING', 'OFFBOARDING']);

function DeleteClientButton({ client, onDeleted, onError }) {
  const [deleting, setDeleting] = useState(false);
  const blocked = CASE_IN_PROGRESS.has(client.status);

  const onClick = async () => {
    const confirmed = window.confirm(
      `Permanently delete ${client.name}?\n\nThis also deletes the history of its finished cases. It can't be undone.`,
    );
    if (!confirmed) return;
    setDeleting(true);
    try {
      await deleteClient(client.id);
      onDeleted?.(client);
    } catch (err) {
      onError?.(err.message || 'Could not delete the client.');
      setDeleting(false);
    }
  };

  return html`<button
    type="button"
    className="btn btn-danger btn-small"
    onClick=${onClick}
    disabled=${blocked || deleting}
    title=${blocked ? 'Finish the case in progress before deleting this client' : undefined}
    aria-label=${`Delete ${client.name}`}
  >
    ${deleting ? 'Deleting…' : 'Delete'}
  </button>`;
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const COUNTRY_PATTERN = /^[A-Z]{2}$/;
const EMPTY_FORM = { name: '', email: '', country: '' };

export function validateClientForm({ name, email, country }) {
  const errors = {};
  if (!name.trim()) errors.name = 'Name is required.';
  else if (name.trim().length > 200) errors.name = 'Name must be at most 200 characters.';
  if (!email.trim()) errors.email = 'Email is required.';
  else if (!EMAIL_PATTERN.test(email.trim())) errors.email = 'Enter a valid email address.';
  if (!country.trim()) errors.country = 'Country is required.';
  else if (!COUNTRY_PATTERN.test(country.trim())) errors.country = 'Use a 2-letter ISO code, e.g. ES.';
  return errors;
}

function Field({ id, label, error, hint, children }) {
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined;
  return html`
    <div className=${`field${error ? ' field-invalid' : ''}`}>
      <label htmlFor=${id}>${label}</label>
      ${children({ id, 'aria-invalid': error ? 'true' : undefined, 'aria-describedby': describedBy })}
      ${hint && html`<small id=${hintId} className="muted">${hint}</small>`}
      ${error && html`<small id=${errorId} className="field-error">${error}</small>`}
    </div>
  `;
}

function NewClientForm({ onCreated }) {
  const baseId = useId();
  const [values, setValues] = useState(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [created, setCreated] = useState(null);

  const update = (field) => (event) => {
    let value = event.target.value;
    if (field === 'country') value = value.toUpperCase();
    setValues((prev) => ({ ...prev, [field]: value }));
    setFieldErrors((prev) => (prev[field] ? { ...prev, [field]: undefined } : prev));
  };

  const onSubmit = async (event) => {
    event.preventDefault();
    setFormError(null);
    setCreated(null);
    const errors = validateClientForm(values);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    try {
      const client = await createClient({
        name: values.name.trim(),
        email: values.email.trim(),
        country: values.country.trim(),
      });
      setValues(EMPTY_FORM);
      setCreated(client);
      onCreated?.(client);
    } catch (err) {
      setFieldErrors(err.fieldErrors ?? {});
      setFormError(err.message || 'Could not create the client.');
    } finally {
      setSubmitting(false);
    }
  };

  const extraFieldErrors = Object.entries(fieldErrors).filter(
    ([field, message]) => message && !(field in EMPTY_FORM),
  );

  return html`
    <form className="card form" onSubmit=${onSubmit} noValidate aria-labelledby=${`${baseId}-title`}>
      <h2 id=${`${baseId}-title`}>New client</h2>
      ${formError &&
      html`<div className="alert alert-error" role="alert">
        <p>${formError}</p>
        ${extraFieldErrors.length > 0 &&
        html`<ul>
          ${extraFieldErrors.map(([field, message]) => html`<li key=${field}><strong>${field}</strong>: ${message}</li>`)}
        </ul>`}
      </div>`}
      ${created &&
      html`<div className="alert alert-success" role="status">
        Client created. <a href=${clientHref(created.id)}>Open ${created.name}</a>
      </div>`}
      <div className="form-grid">
        <${Field} id=${`${baseId}-name`} label="Name" error=${fieldErrors.name}>
          ${(props) => html`<input
            ...${props}
            type="text"
            name="name"
            autoComplete="organization"
            maxLength=${200}
            required
            value=${values.name}
            onChange=${update('name')}
          />`}
        <//>
        <${Field} id=${`${baseId}-email`} label="Email" error=${fieldErrors.email}>
          ${(props) => html`<input
            ...${props}
            type="email"
            name="email"
            autoComplete="email"
            maxLength=${320}
            required
            value=${values.email}
            onChange=${update('email')}
          />`}
        <//>
        <${Field} id=${`${baseId}-country`} label="Country" hint="ISO alpha-2, e.g. ES" error=${fieldErrors.country}>
          ${(props) => html`<input
            ...${props}
            type="text"
            name="country"
            autoComplete="country"
            inputMode="text"
            maxLength=${2}
            pattern="[A-Z]{2}"
            required
            className="input-country"
            value=${values.country}
            onChange=${update('country')}
          />`}
        <//>
      </div>
      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled=${submitting}>
          ${submitting ? 'Creating…' : 'Create client'}
        </button>
      </div>
    </form>
  `;
}

export function ClientList() {
  const [clients, setClients] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  const load = useCallback(async (signal) => {
    try {
      const data = await listClients({ signal });
      setClients(Array.isArray(data) ? data : []);
      setError(null);
    } catch (err) {
      if (err?.name === 'AbortError') return;
      setError(err.message || 'Could not load clients.');
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal);
    return () => controller.abort();
  }, [load]);

  return html`
    <section className="stack">
      <div className="page-head">
        <h1>Clients</h1>
        <button type="button" className="btn" onClick=${() => load()}>Refresh</button>
      </div>

      ${error && html`<div className="alert alert-error" role="alert">${error}</div>`}
      ${notice && html`<div className="alert alert-success" role="status">${notice}</div>`}

      <div className="card table-card">
        ${clients === null && !error && html`<p className="muted" role="status">Loading clients…</p>`}
        ${clients !== null && clients.length === 0 && html`<p className="muted">No clients yet. Create one below.</p>`}
        ${clients !== null &&
        clients.length > 0 &&
        html`<div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col">Email</th>
                <th scope="col">Country</th>
                <th scope="col">Status</th>
                <th scope="col"><span className="visually-hidden">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              ${clients.map(
                (client) => html`<tr key=${client.id}>
                  <td data-label="Name"><a href=${clientHref(client.id)}>${client.name}</a></td>
                  <td data-label="Email">${client.email}</td>
                  <td data-label="Country">${client.country}</td>
                  <td data-label="Status"><${StatusBadge} status=${client.status} /></td>
                  <td data-label="Actions">
                    <${DeleteClientButton}
                      client=${client}
                      onDeleted=${(deleted) => {
                        setError(null);
                        setNotice(`${deleted.name} was deleted.`);
                        load();
                      }}
                      onError=${(message) => {
                        setNotice(null);
                        setError(message);
                        load();
                      }}
                    />
                  </td>
                </tr>`,
              )}
            </tbody>
          </table>
        </div>`}
      </div>

      <${NewClientForm} onCreated=${() => load()} />
    </section>
  `;
}
