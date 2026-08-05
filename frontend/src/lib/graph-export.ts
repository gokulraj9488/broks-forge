/**
 * Dependency-free graph export. Because the graph views compute their own deterministic layout, we can render
 * an export-quality vector image ourselves (pure SVG: rects, text, lines) and rasterize it to PNG on a canvas —
 * no html-to-image, no server round-trip. JSON export is the raw node/edge model for tooling.
 */

export interface ExportNode {
  id: string;
  x: number;
  y: number;
  label: string;
  sub?: string;
  /** Hex accent for the node's left bar. */
  accent?: string;
}

export interface ExportEdge {
  source: string;
  target: string;
  accent?: string;
}

const NODE_W = 190;
const NODE_H = 52;
const PAD = 48;

function escapeXml(value: string): string {
  return value.replace(/[<>&"']/g, (c) =>
    ({ "<": "&lt;", ">": "&gt;", "&": "&amp;", '"': "&quot;", "'": "&#39;" })[c] as string,
  );
}

function clip(value: string, max: number): string {
  return value.length > max ? value.slice(0, max - 1) + "…" : value;
}

export function buildGraphSvg(
  nodes: ExportNode[],
  edges: ExportEdge[],
  title = "Broks Forge — graph export",
): { svg: string; width: number; height: number } {
  if (nodes.length === 0) {
    return { svg: `<svg xmlns="http://www.w3.org/2000/svg" width="200" height="80"></svg>`, width: 200, height: 80 };
  }
  const minX = Math.min(...nodes.map((n) => n.x));
  const minY = Math.min(...nodes.map((n) => n.y));
  const maxX = Math.max(...nodes.map((n) => n.x + NODE_W));
  const maxY = Math.max(...nodes.map((n) => n.y + NODE_H));
  const width = maxX - minX + PAD * 2;
  const height = maxY - minY + PAD * 2 + 24;
  const ox = PAD - minX;
  const oy = PAD - minY + 24;
  const pos = new Map(nodes.map((n) => [n.id, { x: n.x + ox, y: n.y + oy }]));

  const edgeSvg = edges
    .map((e) => {
      const s = pos.get(e.source);
      const t = pos.get(e.target);
      if (!s || !t) return "";
      const x1 = s.x + NODE_W / 2;
      const y1 = s.y + NODE_H;
      const x2 = t.x + NODE_W / 2;
      const y2 = t.y;
      const my = (y1 + y2) / 2;
      return `<path d="M${x1},${y1} C${x1},${my} ${x2},${my} ${x2},${y2}" fill="none" stroke="${e.accent ?? "#3f3f46"}" stroke-width="1.5" opacity="0.7"/>`;
    })
    .join("");

  const nodeSvg = nodes
    .map((n) => {
      const p = pos.get(n.id)!;
      const accent = n.accent ?? "#6366f1";
      const label = escapeXml(clip(n.label, 24));
      const sub = n.sub ? escapeXml(clip(n.sub, 26)) : "";
      return `<g transform="translate(${p.x},${p.y})">
  <rect width="${NODE_W}" height="${NODE_H}" rx="8" fill="#18181b" stroke="#27272a"/>
  <rect width="4" height="${NODE_H}" rx="2" fill="${accent}"/>
  <text x="14" y="${sub ? 22 : 30}" fill="#fafafa" font-family="Inter,system-ui,sans-serif" font-size="13" font-weight="600">${label}</text>
  ${sub ? `<text x="14" y="38" fill="#a1a1aa" font-family="Inter,system-ui,sans-serif" font-size="11">${sub}</text>` : ""}
</g>`;
    })
    .join("");

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
  <rect width="${width}" height="${height}" fill="#09090b"/>
  <text x="${PAD}" y="28" fill="#71717a" font-family="Inter,system-ui,sans-serif" font-size="12">${escapeXml(title)}</text>
  ${edgeSvg}
  ${nodeSvg}
</svg>`;
  return { svg, width, height };
}

export function downloadBlob(filename: string, blob: Blob): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export function downloadJson(filename: string, data: unknown): void {
  downloadBlob(filename, new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }));
}

export function downloadSvg(filename: string, svg: string): void {
  downloadBlob(filename, new Blob([svg], { type: "image/svg+xml" }));
}

/** Rasterizes the pure-SVG export to a PNG via an offscreen canvas. Browser-native, no dependencies. */
export function downloadPng(filename: string, svg: string, width: number, height: number, scale = 2): Promise<void> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    const blob = new Blob([svg], { type: "image/svg+xml;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    img.onload = () => {
      const canvas = document.createElement("canvas");
      canvas.width = Math.max(1, Math.round(width * scale));
      canvas.height = Math.max(1, Math.round(height * scale));
      const ctx = canvas.getContext("2d");
      if (!ctx) {
        URL.revokeObjectURL(url);
        reject(new Error("Canvas unsupported"));
        return;
      }
      ctx.scale(scale, scale);
      ctx.drawImage(img, 0, 0);
      URL.revokeObjectURL(url);
      canvas.toBlob((out) => {
        if (out) {
          downloadBlob(filename, out);
          resolve();
        } else {
          reject(new Error("PNG encoding failed"));
        }
      }, "image/png");
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error("SVG render failed"));
    };
    img.src = url;
  });
}
