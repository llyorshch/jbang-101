// Minimal fetch wrapper for the clients backend (same origin, JSON in/out).
// Errors follow RFC 7807 (application/problem+json) and are surfaced as ApiError.

const JSON_CONTENT_TYPE = /^application\/(?:problem\+)?json\b/i;
const ACTION_PATTERN = /^[a-z]+(?:-[a-z]+)*$/;

export class ApiError extends Error {
  constructor({ message, status, title, fieldErrors }) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.title = title ?? null;
    this.fieldErrors = fieldErrors ?? {};
  }
}

/** Keep only string -> string pairs from a problem's `errors` object. */
function sanitizeFieldErrors(errors) {
  const result = {};
  if (errors && typeof errors === 'object' && !Array.isArray(errors)) {
    for (const [field, message] of Object.entries(errors)) {
      if (typeof message === 'string') result[field] = message;
    }
  }
  return result;
}

async function request(method, path, { body, signal } = {}) {
  const headers = { Accept: 'application/json, application/problem+json' };
  const init = { method, headers, signal, credentials: 'same-origin', cache: 'no-store' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }

  let response;
  try {
    response = await fetch(path, init);
  } catch (err) {
    if (err?.name === 'AbortError') throw err;
    throw new ApiError({ message: 'Could not reach the server. Check your connection and try again.', status: 0 });
  }

  let data = null;
  const contentType = response.headers.get('Content-Type') ?? '';
  if (response.status !== 204 && JSON_CONTENT_TYPE.test(contentType)) {
    try {
      data = await response.json();
    } catch {
      data = null;
    }
  }

  if (!response.ok) {
    const title = typeof data?.title === 'string' ? data.title : null;
    const detail = typeof data?.detail === 'string' && data.detail ? data.detail : null;
    throw new ApiError({
      message: detail ?? title ?? `Request failed with status ${response.status}`,
      status: response.status,
      title,
      fieldErrors: sanitizeFieldErrors(data?.errors),
    });
  }
  return data;
}

const segment = (value) => encodeURIComponent(String(value));

function actionSegment(action) {
  if (typeof action !== 'string' || !ACTION_PATTERN.test(action)) {
    throw new ApiError({ message: 'Unsupported action.', status: 0 });
  }
  return segment(action);
}

/** Build an action body without empty fields. */
function actionBody({ note, outcome } = {}) {
  const body = {};
  const trimmed = typeof note === 'string' ? note.trim() : '';
  if (trimmed) body.note = trimmed;
  if (outcome) body.outcome = outcome;
  return body;
}

// Clients
export const listClients = (opts) => request('GET', '/api/clients', opts);
export const createClient = ({ name, email, country }, opts) =>
  request('POST', '/api/clients', { ...opts, body: { name, email, country } });
export const getClient = (clientId, opts) => request('GET', `/api/clients/${segment(clientId)}`, opts);
export const listClientCases = (clientId, opts) =>
  request('GET', `/api/clients/${segment(clientId)}/cases`, opts);

// Case creation
export const startOnboarding = (clientId, opts) =>
  request('POST', `/api/clients/${segment(clientId)}/onboardings`, opts);
export const startOffboarding = (clientId, opts) =>
  request('POST', `/api/clients/${segment(clientId)}/offboardings`, opts);

// Cases
export const getOnboarding = (caseId, opts) => request('GET', `/api/onboardings/${segment(caseId)}`, opts);
export const getOffboarding = (caseId, opts) => request('GET', `/api/offboardings/${segment(caseId)}`, opts);

export const performOnboardingAction = (caseId, action, payload, opts) =>
  request('POST', `/api/onboardings/${segment(caseId)}/actions/${actionSegment(action)}`, {
    ...opts,
    body: actionBody(payload),
  });

export const performOffboardingAction = (caseId, action, payload, opts) =>
  request('POST', `/api/offboardings/${segment(caseId)}/actions/${actionSegment(action)}`, {
    ...opts,
    body: actionBody(payload),
  });

const CASE_RESOURCES = { ONBOARDING: 'onboardings', OFFBOARDING: 'offboardings' };

/**
 * Same-origin URL of the PNG diagram for a case, or null for an unknown type.
 * `version` is a cache buster so the image always reflects the current state.
 */
export function caseDiagramUrl(caseView, version = Date.now()) {
  const resource = CASE_RESOURCES[caseView?.type];
  if (!resource || caseView.id == null) return null;
  const v = encodeURIComponent(String(version));
  return `/api/${resource}/${segment(caseView.id)}/diagram?v=${v}`;
}

/** Dispatch an action to the right endpoint for the case type. */
export function performCaseAction(caseView, action, payload, opts) {
  if (caseView?.type === 'ONBOARDING') return performOnboardingAction(caseView.id, action, payload, opts);
  if (caseView?.type === 'OFFBOARDING') return performOffboardingAction(caseView.id, action, payload, opts);
  return Promise.reject(new ApiError({ message: 'Unknown case type.', status: 0 }));
}
