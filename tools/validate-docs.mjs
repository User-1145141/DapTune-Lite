import {
  existsSync,
  readFileSync,
  readdirSync,
  statSync,
} from "node:fs";
import path from "node:path";

const repositoryRoot = process.cwd();
const ignoredDirectories = new Set([
  ".git",
  ".gradle",
  ".idea",
  ".kotlin",
  "build",
  "node_modules",
]);
const failures = [];

function collectMarkdownFiles(directory) {
  const files = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.isDirectory() && ignoredDirectories.has(entry.name)) continue;
    const absolutePath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...collectMarkdownFiles(absolutePath));
    } else if (path.extname(absolutePath).toLowerCase() === ".md") {
      files.push(absolutePath);
    }
  }
  return files;
}

function relative(file) {
  return path.relative(repositoryRoot, file).replaceAll(path.sep, "/");
}

function githubHeadingSlugs(file) {
  const occurrences = new Map();
  const slugs = new Set();

  for (const line of readFileSync(file, "utf8").split(/\r?\n/)) {
    const heading = /^(#{1,6})\s+(.+?)\s*#*\s*$/.exec(line);
    if (heading == null) continue;

    const base = heading[2]
      .replace(/<[^>]*>/g, "")
      .replace(/!??\[([^\]]+)]\([^)]+\)/g, "$1")
      .replace(/`([^`]*)`/g, "$1")
      .toLowerCase()
      .trim()
      .replace(/[^\p{L}\p{N}\s_-]/gu, "")
      .replace(/\s+/g, "-");
    const occurrence = occurrences.get(base) ?? 0;
    occurrences.set(base, occurrence + 1);
    slugs.add(occurrence === 0 ? base : `${base}-${occurrence}`);
  }

  return slugs;
}

const markdownFiles = collectMarkdownFiles(repositoryRoot).sort();
const slugCache = new Map();

function removeFencedCode(markdown) {
  let fenceCharacter = null;
  return markdown
    .split(/\r?\n/)
    .map((line) => {
      const fence = /^\s*(`{3,}|~{3,})/.exec(line);
      if (fence != null) {
        const character = fence[1][0];
        if (fenceCharacter == null) {
          fenceCharacter = character;
        } else if (fenceCharacter === character) {
          fenceCharacter = null;
        }
        return "";
      }
      return fenceCharacter == null ? line : "";
    })
    .join("\n");
}

function checkLocalLink(source, rawTarget) {
  let target = rawTarget.trim();
  if (target.startsWith("<") && target.endsWith(">")) {
    target = target.slice(1, -1);
  }
  target = target.replace(/\s+["'][^"']*["']$/, "");
  if (target === "" || /^(https?:|mailto:|tel:|data:)/i.test(target)) return;

  const hashIndex = target.indexOf("#");
  const filePart = hashIndex >= 0 ? target.slice(0, hashIndex) : target;
  const anchor = hashIndex >= 0 ? target.slice(hashIndex + 1) : "";
  const destination = filePart === ""
    ? source
    : path.resolve(path.dirname(source), decodeURIComponent(filePart));

  if (!destination.startsWith(repositoryRoot + path.sep) && destination !== repositoryRoot) {
    failures.push(`${relative(source)}: link escapes repository: ${target}`);
    return;
  }
  if (!existsSync(destination)) {
    failures.push(`${relative(source)}: missing local target: ${target}`);
    return;
  }
  if (anchor === "" || statSync(destination).isDirectory()) return;
  if (path.extname(destination).toLowerCase() !== ".md") return;

  const destinationSlugs = slugCache.get(destination) ?? githubHeadingSlugs(destination);
  slugCache.set(destination, destinationSlugs);
  const decodedAnchor = decodeURIComponent(anchor).toLowerCase();
  if (!destinationSlugs.has(decodedAnchor)) {
    failures.push(`${relative(source)}: missing heading anchor: ${target}`);
  }
}

for (const file of markdownFiles) {
  const markdown = removeFencedCode(readFileSync(file, "utf8"));
  for (const match of markdown.matchAll(/!?\[[^\]]*]\(([^)]+)\)/g)) {
    checkLocalLink(file, match[1]);
  }
  for (const match of markdown.matchAll(/^\s*\[[^\]]+]:\s*(\S+)/gm)) {
    checkLocalLink(file, match[1]);
  }
}

if (failures.length > 0) {
  console.error(failures.join("\n"));
  process.exitCode = 1;
} else {
  console.log(`Documentation validation passed: ${markdownFiles.length} Markdown files.`);
}
