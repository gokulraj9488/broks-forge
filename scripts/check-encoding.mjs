#!/usr/bin/env node
/*
 * Fails the build if any source file contains UTF-8 that was decoded as Windows-1252 and written
 * back ("mojibake").
 *
 * How this happens: a tool reads a UTF-8 file as CP1252, so an em dash (U+2014 = E2 80 94) becomes
 * the three characters "a-circumflex, euro, right-double-quote", and writing that back as UTF-8
 * bakes the damage into the file. Windows PowerShell 5.1 does exactly this by default —
 * `Get-Content` assumes the ANSI codepage and `Set-Content -Encoding utf8` writes UTF-8 (with a
 * BOM), so a read-modify-write round-trip silently corrupts every non-ASCII character.
 *
 * Detection is by reversal rather than by a blocklist of bad strings: take each suspect run, map
 * every character back to the CP1252 byte it would have come from, and try to decode those bytes
 * as UTF-8. Only a run that decodes cleanly into something *different* is real damage — genuine
 * prose ("señor", "naïve") either fails to map or decodes back to itself, so it is never flagged.
 *
 *   node scripts/check-encoding.mjs          # check, exit 1 on damage
 *   node scripts/check-encoding.mjs --list   # also print every occurrence
 */
import { readdirSync, statSync, readFileSync } from "node:fs";
import { join, extname, relative } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(fileURLToPath(new URL(".", import.meta.url)), "..");
const LIST = process.argv.includes("--list");

const SKIP_DIRS = new Set(["node_modules", ".next", "target", ".git", "dist", "build", ".turbo"]);
const TEXT_EXT = new Set([
  ".ts", ".tsx", ".js", ".jsx", ".mjs", ".cjs", ".json", ".md", ".mdx", ".css", ".scss",
  ".java", ".yml", ".yaml", ".xml", ".properties", ".sql", ".html", ".txt", ".svg", ".sh",
]);

/** CP1252 maps 0x80-0x9F to these code points; the rest of 0x00-0xFF is Latin-1 identity. */
const CP1252_HIGH = {
  0x20ac: 0x80, 0x201a: 0x82, 0x0192: 0x83, 0x201e: 0x84, 0x2026: 0x85, 0x2020: 0x86,
  0x2021: 0x87, 0x02c6: 0x88, 0x2030: 0x89, 0x0160: 0x8a, 0x2039: 0x8b, 0x0152: 0x8c,
  0x017d: 0x8e, 0x2018: 0x91, 0x2019: 0x92, 0x201c: 0x93, 0x201d: 0x94, 0x2022: 0x95,
  0x2013: 0x96, 0x2014: 0x97, 0x02dc: 0x98, 0x2122: 0x99, 0x0161: 0x9a, 0x203a: 0x9b,
  0x0153: 0x9c, 0x017e: 0x9e, 0x0178: 0x9f,
};

const LEAD = "ÂÃâÅå";
const TAIL =
  "-ÿ€‚ƒ„…†‡ˆ‰Š‹Œ" +
  "Ž‘’“”•–—˜™š›œžŸ";
const SUSPECT = new RegExp(`[${LEAD}][${TAIL}]+`, "g");
const strict = new TextDecoder("utf-8", { fatal: true });

function reverse(run) {
  const bytes = [];
  for (const ch of run) {
    const cp = ch.codePointAt(0);
    if (cp <= 0xff) bytes.push(cp);
    else if (CP1252_HIGH[cp] !== undefined) bytes.push(CP1252_HIGH[cp]);
    else return null;
  }
  try {
    const decoded = strict.decode(Buffer.from(bytes));
    return decoded === run ? null : decoded;
  } catch {
    return null;
  }
}

const files = [];
(function walk(dir) {
  let entries;
  try { entries = readdirSync(dir); } catch { return; }
  for (const e of entries) {
    if (SKIP_DIRS.has(e)) continue;
    const p = join(dir, e);
    let st;
    try { st = statSync(p); } catch { continue; }
    if (st.isDirectory()) walk(p);
    else if (TEXT_EXT.has(extname(e).toLowerCase())) files.push(p);
  }
})(ROOT);

const damaged = [];
for (const f of files) {
  const text = readFileSync(f).toString("utf8");
  const hits = [];
  for (const m of text.matchAll(SUSPECT)) {
    const fixed = reverse(m[0]);
    if (fixed) hits.push({ line: text.slice(0, m.index).split("\n").length, found: m[0], shouldBe: fixed });
  }
  if (hits.length) damaged.push({ file: relative(ROOT, f), hits });
}

const total = damaged.reduce((n, d) => n + d.hits.length, 0);
if (!damaged.length) {
  console.log(`encoding OK - ${files.length} source files, no mojibake`);
  process.exit(0);
}

console.error(`\nMOJIBAKE DETECTED in ${damaged.length} file(s), ${total} occurrence(s).\n`);
for (const d of damaged) {
  console.error(`  ${d.file}`);
  for (const h of LIST ? d.hits : d.hits.slice(0, 3)) {
    console.error(`      line ${h.line}: ${JSON.stringify(h.found)} should be ${JSON.stringify(h.shouldBe)}`);
  }
  if (!LIST && d.hits.length > 3) console.error(`      ... and ${d.hits.length - 3} more`);
}
console.error(
  "\nThese files were read as Windows-1252 and written back as UTF-8." +
  "\nDo not hand-edit the characters - re-save the file as UTF-8 from the original text." +
  "\nOn Windows, avoid PowerShell 5.1 Get-Content/Set-Content for source files; use" +
  "\n[System.IO.File]::ReadAllText/WriteAllText with UTF8Encoding($false), or an editor.\n",
);
process.exit(1);
