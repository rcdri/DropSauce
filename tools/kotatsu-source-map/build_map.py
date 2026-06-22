#!/usr/bin/env python3
"""Build Kotatsu-source -> Mihon-source-id mapping from parser repos + keiyoushi index."""
import json, os, re, sys
from collections import defaultdict

SCRATCH = os.path.dirname(os.path.abspath(__file__))

# ---------- 1. keiyoushi (Mihon) side ----------
with open(os.path.join(SCRATCH, "keiyoushi_index.json"), encoding="utf-8") as f:
    keiyoushi = json.load(f)

def reg_domain(url_or_host):
    """Reduce a url/host to a registrable-ish domain key (last 2-3 labels)."""
    if not url_or_host:
        return None
    h = re.sub(r"^https?://", "", url_or_host.strip().lower())
    h = h.split("/")[0].split(":")[0]
    h = re.sub(r"^www\.", "", h)
    parts = h.split(".")
    if len(parts) <= 2:
        return h
    # handle co.uk style
    if parts[-2] in ("co", "com", "org", "net", "gov", "edu") and len(parts[-1]) == 2:
        return ".".join(parts[-3:])
    return ".".join(parts[-2:])

def norm_name(s):
    return re.sub(r"[^a-z0-9]", "", (s or "").lower())

mihon_sources = []  # {name, norm, lang, id, domain, pkg}
mihon_by_domain = defaultdict(list)
mihon_by_norm = defaultdict(list)
for ext in keiyoushi:
    pkg = ext.get("pkg", "")
    for s in ext.get("sources", []):
        rec = {
            "name": s.get("name", ""),
            "norm": norm_name(s.get("name", "")),
            "lang": s.get("lang", ""),
            "id": str(s.get("id", "")),
            "domain": reg_domain(s.get("baseUrl", "")),
            "baseUrl": s.get("baseUrl", ""),
            "pkg": pkg,
        }
        mihon_sources.append(rec)
        if rec["domain"]:
            mihon_by_domain[rec["domain"]].append(rec)
        if rec["norm"]:
            mihon_by_norm[rec["norm"]].append(rec)

print(f"[mihon] {len(mihon_sources)} sources, {len(mihon_by_domain)} domains, {len(mihon_by_norm)} norm-names")

# ---------- 2. Kotatsu side: scan parser repos ----------
ANNO = re.compile(r'@MangaSourceParser\(\s*"([^"]+)"\s*,\s*"([^"]*)"(?:\s*,\s*"([^"]*)")?')
DOMAIN_LIT = re.compile(r'ConfigKey\.Domain\(\s*((?:"[^"]+"\s*,?\s*)+)', re.S)
STRLIT = re.compile(r'"([^"]+)"')

def scan_repo(root, fork):
    out = []
    base = os.path.join(root, "src", "main", "kotlin")
    for dirpath, _, files in os.walk(base):
        for fn in files:
            if not fn.endswith(".kt"):
                continue
            path = os.path.join(dirpath, fn)
            try:
                txt = open(path, encoding="utf-8").read()
            except Exception:
                continue
            annos = ANNO.findall(txt)
            if not annos:
                continue
            # collect candidate domains in this file
            domains = []
            for m in DOMAIN_LIT.finditer(txt):
                for lit in STRLIT.findall(m.group(1)):
                    d = reg_domain(lit)
                    if d:
                        domains.append(d)
            # also raw string literals that look like domains (fallback)
            for lit in STRLIT.findall(txt):
                if re.match(r'^[a-z0-9.-]+\.[a-z]{2,}$', lit) and "." in lit and "/" not in lit and " " not in lit:
                    d = reg_domain(lit)
                    if d:
                        domains.append(d)
            domset = list(dict.fromkeys(domains))  # dedup, keep order
            for (name, title, locale) in annos:
                out.append({
                    "enum": name, "title": title, "locale": locale or "",
                    "domains": domset, "fork": fork, "file": os.path.relpath(path, root),
                })
    return out

redo = scan_repo(os.path.join(SCRATCH, "redo-parsers"), "redo")
orig = scan_repo(os.path.join(SCRATCH, "orig-parsers"), "orig")

# union by enum name, prefer redo metadata, track forks present
kot = {}
for rec in redo + orig:
    e = rec["enum"]
    if e not in kot:
        kot[e] = dict(rec); kot[e]["forks"] = set()
    kot[e]["forks"].add(rec["fork"])
    # merge domains
    for d in rec["domains"]:
        if d not in kot[e]["domains"]:
            kot[e]["domains"].append(d)
print(f"[kotatsu] redo={len(set(r['enum'] for r in redo))} orig={len(set(r['enum'] for r in orig))} union={len(kot)}")

# ---------- 3. match ----------
def pick_lang(cands, locale):
    """Among same-site mihon candidates, pick best by language preference."""
    locale = (locale or "").lower()
    base = locale.split("-")[0]
    # exact lang
    for c in cands:
        if c["lang"].lower() == locale and locale:
            return c
    for c in cands:
        if c["lang"].lower().split("-")[0] == base and base:
            return c
    for pref in ("all", "en"):
        for c in cands:
            if c["lang"].lower() == pref:
                return c
    return cands[0]

# Curated layer (easy to extend as more forks are added).
# OVERRIDES: force enum -> explicit mihon source id (wins over auto-match).
OVERRIDES = {}
# EXCLUDES: enums to never emit even if auto-matched (verified-wrong links).
# MANGAPARK: real mangapark.net is NOT on keiyoushi; the only "Manga-Park" there is an
# unrelated knockoff (manga-park.com, ja) -> would mis-link. Leave unmapped (skip).
EXCLUDES = {"MANGAPARK"}

by_id = {s["id"]: s for s in mihon_sources}

results = {}
stats = defaultdict(int)
for e, k in kot.items():
    if e in EXCLUDES:
        stats["excluded"] += 1
        continue
    if e in OVERRIDES:
        m = by_id.get(str(OVERRIDES[e]))
        if m:
            results[e] = {
                "enum": e, "title": k["title"], "locale": k["locale"], "forks": sorted(k["forks"]),
                "mihonId": m["id"], "mihonName": m["name"], "mihonLang": m["lang"],
                "mihonDomain": m["domain"], "mihonPkg": m["pkg"], "method": "override",
            }
            stats["override"] += 1
            continue
    ntitle = norm_name(k["title"])
    match = None; method = None
    # 1. domain match
    dom_cands = []
    for d in k["domains"]:
        dom_cands += mihon_by_domain.get(d, [])
    if dom_cands:
        # refine domain candidates by title if possible
        same_title = [c for c in dom_cands if c["norm"] == ntitle]
        pool = same_title or dom_cands
        match = pick_lang(pool, k["locale"])
        method = "domain+title" if same_title else "domain"
    # 2. exact normalized title match
    if match is None and ntitle and ntitle in mihon_by_norm:
        cands = mihon_by_norm[ntitle]
        match = pick_lang(cands, k["locale"])
        method = "title"
    if match:
        results[e] = {
            "enum": e, "title": k["title"], "locale": k["locale"],
            "forks": sorted(k["forks"]),
            "mihonId": match["id"], "mihonName": match["name"],
            "mihonLang": match["lang"], "mihonDomain": match["domain"],
            "mihonPkg": match["pkg"], "method": method,
        }
        stats[method] += 1
    else:
        stats["unmatched"] += 1

print("[match]", dict(stats), f"total kotatsu={len(kot)} matched={len(results)}")

with open(os.path.join(SCRATCH, "mapping_full.json"), "w", encoding="utf-8") as f:
    json.dump(sorted(results.values(), key=lambda r: r["enum"]), f, indent=1, ensure_ascii=False)

# unmatched list for review
unmatched = sorted(e for e in kot if e not in results)
with open(os.path.join(SCRATCH, "unmatched.json"), "w", encoding="utf-8") as f:
    json.dump([{"enum": e, "title": kot[e]["title"], "locale": kot[e]["locale"],
                "domains": kot[e]["domains"], "forks": sorted(kot[e]["forks"])} for e in unmatched],
              f, indent=1, ensure_ascii=False)
print(f"wrote mapping_full.json ({len(results)}) and unmatched.json ({len(unmatched)})")

# ---------- 4. emit baked asset for the app ----------
asset = {
    "version": 1,
    "_comment": "Kotatsu parser source-name -> Mihon source. Generated by build_map.py "
                "(kotatsu-parsers-redo + kotatsu-parsers vs keiyoushi index). id is the Mihon "
                "CatalogueSource.id (resolve via MihonExtensionManager.getById).",
    "entries": {
        e: {"id": r["mihonId"], "name": r["mihonName"], "lang": r["mihonLang"], "pkg": r["mihonPkg"]}
        for e, r in sorted(results.items())
    },
}
asset_path = os.path.join(SCRATCH, "kotatsu_source_map.json")
with open(asset_path, "w", encoding="utf-8") as f:
    json.dump(asset, f, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
print(f"wrote asset {asset_path} ({len(asset['entries'])} entries, {os.path.getsize(asset_path)} bytes)")

# dump all kotatsu sources (for verification tooling)
with open(os.path.join(SCRATCH, "kotatsu_sources.json"), "w", encoding="utf-8") as f:
    json.dump({e: {"title": k["title"], "locale": k["locale"], "domains": k["domains"],
                   "forks": sorted(k["forks"])} for e, k in sorted(kot.items())},
              f, ensure_ascii=False, indent=0)

# dump mihon domain index too
with open(os.path.join(SCRATCH, "mihon_by_domain.json"), "w", encoding="utf-8") as f:
    json.dump({d: [{"name": c["name"], "lang": c["lang"], "id": c["id"]} for c in cs]
               for d, cs in sorted(mihon_by_domain.items())}, f, ensure_ascii=False, indent=0)
