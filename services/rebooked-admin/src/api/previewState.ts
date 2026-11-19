export type PreviewDataState = "data" | "loading" | "empty" | "error";

const previewStates: PreviewDataState[] = ["data", "loading", "empty", "error"];

function readPreviewState(): PreviewDataState {
  if (!import.meta.env.DEV) return "data";
  const queryState = new URLSearchParams(window.location.search).get("previewState");
  if (queryState && previewStates.includes(queryState as PreviewDataState)) {
    return queryState as PreviewDataState;
  }
  try {
    const storedState = localStorage.getItem("previewState");
    if (storedState && previewStates.includes(storedState as PreviewDataState)) {
      return storedState as PreviewDataState;
    }
  } catch {
    return "data";
  }
  return "data";
}

let currentState = readPreviewState();

export function getPreviewDataState(): PreviewDataState {
  return import.meta.env.DEV ? currentState : "data";
}

export function setPreviewDataState(state: PreviewDataState): void {
  if (!import.meta.env.DEV) return;
  currentState = state;
  try {
    localStorage.setItem("previewState", state);
  } catch {
    // The query parameter carries the selection when storage is unavailable.
  }
  const url = new URL(window.location.href);
  url.searchParams.set("previewState", state);
  window.history.replaceState(null, "", url);
  window.location.reload();
}
