import { Extension, Node as TiptapNode } from "@tiptap/core";

const managedMedia = /^\/api\/media\/[a-f0-9-]{36}$/;

export function videoSource(raw: string): string | null {
  const value = raw.trim();
  if (managedMedia.test(value)) return value;
  if (!/^https:\/\/[^/\\\s]/i.test(value) || /[\s\\]/.test(value)) return null;
  try {
    const url = new URL(value);
    return url.hostname && !url.username && !url.password ? url.href : null;
  } catch {
    return null;
  }
}

const videoAttributes = { controls: "", playsinline: "", preload: "metadata" };

export const compatibleVideoFormat = "H.264 视频 / AAC 音频的 MP4";

export function canLoadLocalVideo(file: File): Promise<boolean> {
  return new Promise((resolve) => {
    const video = document.createElement("video");
    const url = URL.createObjectURL(file);
    const finish = (playable: boolean) => {
      clearTimeout(timeout);
      video.onloadeddata = video.onerror = null;
      video.removeAttribute("src");
      video.load();
      URL.revokeObjectURL(url);
      resolve(playable);
    };
    // Some mobile browsers defer loading frames, so a timeout alone is inconclusive.
    const timeout = window.setTimeout(() => finish(true), 4000);
    video.preload = "auto";
    video.onloadeddata = () => finish(true);
    video.onerror = () => finish(false);
    video.src = url;
  });
}

export const RichVideo = TiptapNode.create({
  name: "richVideo",
  group: "block",
  atom: true,
  addAttributes: () => ({ src: { default: null } }),
  parseHTML() {
    return [
      {
        tag: "video[src]",
        getAttrs: (element) => {
          const src = videoSource(element.getAttribute("src") || "");
          return src ? { src } : false;
        },
      },
    ];
  },
  renderHTML({ node }) {
    return [
      "video",
      { ...videoAttributes, src: videoSource(node.attrs.src || "") },
    ];
  },
  addNodeView() {
    return ({ node, editor, getPos }) => {
      const dom = document.createElement("div");
      dom.className = "rich-video";
      dom.contentEditable = "false";
      const video = document.createElement("video");
      for (const [attribute, value] of Object.entries(videoAttributes))
        video.setAttribute(attribute, value);
      const src = videoSource(node.attrs.src || "");
      const error = document.createElement("p");
      error.className = "inline-error";
      error.setAttribute("role", "alert");
      error.hidden = true;
      error.textContent =
        "视频无法播放。请检查地址，或使用 " + compatibleVideoFormat + "。";
      video.onerror = () => {
        error.hidden = false;
      };
      video.onloadeddata = () => {
        error.hidden = true;
      };
      if (src) video.src = src;
      const remove = document.createElement("button");
      remove.type = "button";
      remove.className = "btn rich-video-remove";
      remove.textContent = "删除视频";
      remove.onclick = () => {
        const position = getPos();
        if (editor.isEditable && typeof position === "number")
          editor
            .chain()
            .focus()
            .deleteRange({ from: position, to: position + node.nodeSize })
            .run();
      };
      dom.append(video, error, remove);
      return {
        dom,
        stopEvent: (event) =>
          event.target instanceof Node && dom.contains(event.target),
      };
    };
  },
});

function cellColor(value: string): string | null {
  if (/^#[0-9a-f]{6}$/i.test(value)) return value;
  if (/^#[0-9a-f]{3}$/i.test(value))
    return (
      "#" + Array.from(value.slice(1), (digit) => digit.repeat(2)).join("")
    );
  const rgb = /^rgb\(\s*(\d{1,3}),\s*(\d{1,3}),\s*(\d{1,3})\s*\)$/i.exec(value);
  if (!rgb) return null;
  const components = rgb.slice(1).map(Number);
  return components.every((number) => number <= 255)
    ? "#" +
        components
          .map((number) => number.toString(16).padStart(2, "0"))
          .join("")
    : null;
}

export const TableCellFormat = Extension.create({
  name: "tableCellFormat",
  addGlobalAttributes() {
    return [
      {
        types: ["tableCell", "tableHeader"],
        attributes: {
          backgroundColor: {
            default: null,
            parseHTML: (cell) =>
              cellColor(
                cell.getAttribute("data-cell-background") ||
                  cell.style.backgroundColor ||
                  cell.getAttribute("bgcolor") ||
                  "",
              ),
            renderHTML: ({ backgroundColor }) => {
              const color = cellColor(backgroundColor || "");
              return color
                ? {
                    "data-cell-background": color,
                    style: "background-color:" + color,
                  }
                : {};
            },
          },
        },
      },
    ];
  },
});

export function normalizePastedTable(html: string): string {
  if (!/<table\b/i.test(html)) return html;
  const doc = new DOMParser().parseFromString(html, "text/html");
  for (const cell of doc.querySelectorAll<HTMLTableCellElement>("td, th")) {
    if (cell.hasAttribute("colwidth") || cell.colSpan !== 1) continue;
    const width = /^(\d+(?:\.\d+)?)(px|pt)?$/i.exec(
      cell.style.width || cell.getAttribute("width") || "",
    );
    if (!width) continue;
    const pixels = Math.round(
      Number(width[1]) * (width[2]?.toLowerCase() === "pt" ? 4 / 3 : 1),
    );
    if (pixels > 0 && pixels <= 9999)
      cell.setAttribute("colwidth", String(pixels));
  }
  return doc.body.innerHTML;
}
