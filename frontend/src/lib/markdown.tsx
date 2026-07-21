import type { ReactNode } from "react";

/**
 * Minimal, dependency-free Markdown → JSX renderer for the /docs route.
 * Covers what the repo's engineering docs actually use — headings, paragraphs,
 * lists, fenced code blocks, blockquotes, tables, links, bold/italic, inline
 * code and rules — not a general-purpose CommonMark implementation. Anything
 * outside that (nested lists, footnotes) renders as plain text rather than
 * throwing, since these are trusted, repo-authored files, not user input.
 */

function renderInline(text: string, keyPrefix: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  // Order matters: code spans first so ** or _ inside `code` isn't touched.
  const pattern = /`([^`]+)`|\*\*([^*]+)\*\*|\*([^*]+)\*|\[([^\]]+)\]\(([^)]+)\)/g;
  let last = 0;
  let match: RegExpExecArray | null;
  let i = 0;
  while ((match = pattern.exec(text)) !== null) {
    if (match.index > last) nodes.push(text.slice(last, match.index));
    if (match[1] !== undefined) {
      nodes.push(
        <code key={`${keyPrefix}-${i}`} className="rounded bg-muted px-1.5 py-0.5 font-mono text-[0.85em]">
          {match[1]}
        </code>,
      );
    } else if (match[2] !== undefined) {
      nodes.push(<strong key={`${keyPrefix}-${i}`}>{match[2]}</strong>);
    } else if (match[3] !== undefined) {
      nodes.push(<em key={`${keyPrefix}-${i}`}>{match[3]}</em>);
    } else if (match[4] !== undefined) {
      const href = match[5];
      const external = /^https?:\/\//.test(href);
      nodes.push(
        <a
          key={`${keyPrefix}-${i}`}
          href={href}
          className="text-primary underline underline-offset-2 hover:no-underline"
          target={external ? "_blank" : undefined}
          rel={external ? "noopener noreferrer" : undefined}
        >
          {match[4]}
        </a>,
      );
    }
    last = pattern.lastIndex;
    i++;
  }
  if (last < text.length) nodes.push(text.slice(last));
  return nodes;
}

function isTableSeparator(line: string): boolean {
  return /^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)+\|?\s*$/.test(line);
}

function splitTableRow(line: string): string[] {
  return line
    .trim()
    .replace(/^\|/, "")
    .replace(/\|$/, "")
    .split("|")
    .map((c) => c.trim());
}

export function renderMarkdown(source: string): ReactNode[] {
  const lines = source.replace(/\r\n/g, "\n").split("\n");
  const blocks: ReactNode[] = [];
  let i = 0;
  let key = 0;

  while (i < lines.length) {
    const line = lines[i];

    if (line.trim() === "") {
      i++;
      continue;
    }

    // Fenced code block
    if (line.trim().startsWith("```")) {
      const lang = line.trim().slice(3).trim();
      const codeLines: string[] = [];
      i++;
      while (i < lines.length && !lines[i].trim().startsWith("```")) {
        codeLines.push(lines[i]);
        i++;
      }
      i++; // skip closing fence
      blocks.push(
        <pre
          key={key++}
          className="my-4 overflow-x-auto rounded-lg border border-border bg-muted/40 p-4 text-[13px] leading-relaxed"
        >
          <code className={lang ? `language-${lang}` : undefined}>{codeLines.join("\n")}</code>
        </pre>,
      );
      continue;
    }

    // Heading
    const heading = /^(#{1,4})\s+(.*)$/.exec(line);
    if (heading) {
      const level = heading[1].length;
      const text = heading[2].trim();
      const cls = [
        "mt-10 mb-4 text-3xl font-semibold tracking-tight text-foreground",
        "mt-8 mb-3 text-2xl font-semibold tracking-tight text-foreground",
        "mt-6 mb-2 text-lg font-semibold text-foreground",
        "mt-5 mb-2 text-base font-semibold text-foreground",
      ][level - 1];
      const content = renderInline(text, `h${key}`);
      blocks.push(
        level === 1 ? (
          <h1 key={key++} className={cls}>{content}</h1>
        ) : level === 2 ? (
          <h2 key={key++} className={cls}>{content}</h2>
        ) : level === 3 ? (
          <h3 key={key++} className={cls}>{content}</h3>
        ) : (
          <h4 key={key++} className={cls}>{content}</h4>
        ),
      );
      i++;
      continue;
    }

    // Horizontal rule
    if (/^-{3,}$/.test(line.trim())) {
      blocks.push(<hr key={key++} className="my-8 border-border/60" />);
      i++;
      continue;
    }

    // Blockquote
    if (line.trim().startsWith(">")) {
      const quoteLines: string[] = [];
      while (i < lines.length && lines[i].trim().startsWith(">")) {
        quoteLines.push(lines[i].trim().replace(/^>\s?/, ""));
        i++;
      }
      blocks.push(
        <blockquote
          key={key++}
          className="my-4 border-l-2 border-primary/40 pl-4 text-muted-foreground"
        >
          {renderInline(quoteLines.join(" "), `bq${key}`)}
        </blockquote>,
      );
      continue;
    }

    // Table (header + separator + rows)
    if (line.includes("|") && i + 1 < lines.length && isTableSeparator(lines[i + 1])) {
      const header = splitTableRow(line);
      i += 2;
      const rows: string[][] = [];
      while (i < lines.length && lines[i].includes("|") && lines[i].trim() !== "") {
        rows.push(splitTableRow(lines[i]));
        i++;
      }
      blocks.push(
        <div key={key++} className="my-4 overflow-x-auto rounded-lg border border-border">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-border bg-muted/40">
                {header.map((h, c) => (
                  <th key={c} className="px-3 py-2 text-left font-medium text-foreground">
                    {renderInline(h, `th${key}-${c}`)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row, r) => (
                <tr key={r} className="border-b border-border/60 last:border-0">
                  {row.map((cell, c) => (
                    <td key={c} className="px-3 py-2 align-top text-muted-foreground">
                      {renderInline(cell, `td${key}-${r}-${c}`)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>,
      );
      continue;
    }

    // List (unordered or ordered), single level
    const listMatch = /^\s*([-*]|\d+\.)\s+(.*)$/.exec(line);
    if (listMatch) {
      const ordered = /\d+\./.test(listMatch[1]);
      const items: string[] = [];
      while (i < lines.length) {
        const m = /^\s*([-*]|\d+\.)\s+(.*)$/.exec(lines[i]);
        if (!m) break;
        items.push(m[2]);
        i++;
      }
      const itemNodes = items.map((item, idx) => (
        <li key={idx}>{renderInline(item, `li${key}-${idx}`)}</li>
      ));
      blocks.push(
        ordered ? (
          <ol key={key++} className="my-3 ml-5 list-decimal space-y-1.5 text-muted-foreground">
            {itemNodes}
          </ol>
        ) : (
          <ul key={key++} className="my-3 ml-5 list-disc space-y-1.5 text-muted-foreground">
            {itemNodes}
          </ul>
        ),
      );
      continue;
    }

    // Paragraph — collect until a blank line or a new block starts
    const paraLines: string[] = [line];
    i++;
    while (
      i < lines.length &&
      lines[i].trim() !== "" &&
      !/^(#{1,4})\s+/.test(lines[i]) &&
      !lines[i].trim().startsWith("```") &&
      !lines[i].trim().startsWith(">") &&
      !/^\s*([-*]|\d+\.)\s+/.test(lines[i])
    ) {
      paraLines.push(lines[i]);
      i++;
    }
    blocks.push(
      <p key={key++} className="my-3 leading-relaxed text-muted-foreground">
        {renderInline(paraLines.join(" "), `p${key}`)}
      </p>,
    );
  }

  return blocks;
}
