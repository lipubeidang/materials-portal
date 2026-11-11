const scriptUrl = "https://www.matbd.cn/elfk/va.js";

type PageView = {
  page: string;
  params: Record<string, string>;
};

declare global {
  interface Window {
    xSend?: (params: Record<string, string>) => void;
  }
}

let pending: PageView[] | undefined;

function send(view: PageView) {
  try {
    window.xSend?.(view.params);
  } catch {
    // Keep an analytics failure from interrupting navigation.
  }
}

export function recordCenterVisit(centerUserName?: string) {
  if (!import.meta.env.PROD) return;

  const { origin, pathname, search } = window.location;
  const view: PageView = {
    page: pathname + search,
    params: {
      "X-Log-Event": "pageview",
      "X-Log-User-Id": centerUserName ?? "",
      // The published SDK reads url before serializing it to x_log_url.
      url: origin + pathname,
    },
  };

  if (window.xSend) {
    send(view);
    return;
  }

  if (pending) {
    pending.push(view);
    return;
  }

  const queued = [view];
  pending = queued;
  const script = document.createElement("script");
  script.src = scriptUrl;
  script.async = true;
  script.onload = () => {
    pending = undefined;
    // va.js has already sent one pageview for the URL at execution time.
    if (queued.at(-1)?.page === location.pathname + location.search)
      queued.pop();
    queued.forEach(send);
  };
  script.onerror = () => {
    pending = undefined;
    script.remove();
  };
  document.head.append(script);
}
