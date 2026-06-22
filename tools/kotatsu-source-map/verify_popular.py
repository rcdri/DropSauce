#!/usr/bin/env python3
# Verify mapping coverage/correctness for the most popular real-world sources.
import json, io, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

def J(p): return json.load(open(p, encoding="utf-8"))
full = {r["enum"]: r for r in J("mapping_full.json")}
ksrc = J("kotatsu_sources.json")            # enum -> {title,locale,domains,forks}
mbd  = J("mihon_by_domain.json")            # domain -> [{name,lang,id}]

# Popular real-world manga/manhwa/manhua + hentai sources users actually have libraries on.
# (domain registrable-key, friendly label)
POPULAR = [
 ("mangadex.org","MangaDex"),("comick.io","Comick"),("comick.fun","Comick"),("bato.to","Bato.to"),
 ("weebcentral.com","Weeb Central"),("mangafire.to","MangaFire"),("mangapark.net","MangaPark"),
 ("mangakakalot.com","Mangakakalot"),("manganato.com","Manganato"),("natomanga.com","NatoManga"),
 ("nelomanga.com","Nelo"),("chapmanganato.com","Manganato"),("mangabuddy.com","MangaBuddy"),
 ("mangapill.com","Mangapill"),("mangahere.cc","MangaHere"),("mangakatana.com","MangaKatana"),
 ("manhuaplus.com","ManhuaPlus"),("mangasee123.com","MangaSee"),("manga4life.com","Manga4Life"),
 ("webtoons.com","Webtoons"),("mangaplus.shueisha.co.jp","MangaPlus"),("shueisha.co.jp","MangaPlus"),
 ("tapas.io","Tapas"),("toonily.com","Toonily"),("toonily.me","Toonily"),("dynasty-scans.com","Dynasty"),
 ("nhentai.net","NHentai"),("hitomi.la","Hitomi"),("e-hentai.org","E-Hentai"),("exhentai.org","ExHentai"),
 ("hentaifox.com","HentaiFox"),("3hentai.net","3Hentai"),("tsumino.com","Tsumino"),("mangago.me","MangaGo"),
 ("readcomiconline.li","ReadComicOnline"),("comicextra.com","ComicExtra"),("mangakomi.io","MangaKomi"),
 ("asuracomic.net","Asura"),("asurascans.com","Asura"),("flamecomics.xyz","Flame Comics"),
 ("flamecomics.me","Flame Comics"),("reaperscans.com","Reaper Scans"),("realmscans.xyz","Realm Scans"),
 ("luminousscans.net","Luminous"),("nightscans.org","Night Scans"),("resetscans.com","Reset Scans"),
 ("kunmanga.com","KunManga"),("manhwaclan.com","ManhwaClan"),("topmanhua.com","TopManhua"),
 ("zinmanga.com","ZinManga"),("coffeemanga.io","CoffeeManga"),("manhuaus.com","ManhuaUS"),
 ("rawkuma.com","Rawkuma"),("senmanga.com","SenManga"),("klmanga.net","KLManga"),
 ("leviatanscans.com","Leviatan"),("zeroscans.com","Zero Scans"),("cosmic-scans.com","Cosmic Scans"),
 ("isekaiscan.com","IsekaiScan"),("mangaowl.io","MangaOwl"),("manhuafast.net","ManhuaFast"),
 ("lermanga.org","LerManga"),("tcbscans.com","TCB Scans"),("tcbscans.me","TCB Scans"),
 # Spanish
 ("lectortmo.com","TuMangaOnline"),("tmofans.com","TuMangaOnline"),("inmanga.com","InManga"),
 ("leercapitulo.com","LeerCapitulo"),("olympusbiblioteca.com","Olympus"),("zonatmo.com","ZonaTMO"),
 # French
 ("sushiscan.net","SushiScan"),("japscan.lol","Japscan"),("scan-vf.net","Scan-VF"),
 ("mangas-origines.fr","Mangas-Origines"),("bentomanga.com","BentoManga"),("lelmanga.com","LelManga"),
 ("mangakawaii.io","MangaKawaii"),("phenix-scans.com","Phenix"),
 # Russian
 ("desu.me","Desu"),("remanga.org","Remanga"),("mangalib.me","MangaLib"),("readmanga.io","ReadManga"),
 ("mintmanga.com","MintManga"),("selfmanga.ru","SelfManga"),
 # Vietnamese
 ("nettruyen.com","NetTruyen"),("truyenqqto.com","TruyenQQ"),("blogtruyen.vn","BlogTruyen"),
 ("cuutruyen.net","CuuTruyen"),
 # Japanese raw
 ("manga1000.com","Manga1000"),("manga1001.com","Manga1001"),("rawkuma.net","Rawkuma"),
 # Hentai extra
 ("hentai2read.com","Hentai2Read"),("imhentai.xxx","ImHentai"),("hentainexus.com","HentaiNexus"),
 ("koharu.to","Koharu"),("schale.network","SchaleNetwork"),("multporn.net","Multporn"),
 ("hanascan.com","HanaScan"),("simply-hentai.com","SimplyHentai"),
 # Manhwa/aggregators more
 ("mangaclash.com","MangaClash"),("manhwatop.com","ManhwaTop"),("1stkissmanga.me","1stKissManga"),
 ("webtoonxyz.com","WebtoonXYZ"),("mangabat.com","MangaBat"),("mangatx.com","MangaTX"),
 ("manytoon.com","ManyToon"),("toonggod.com","ToonGod"),("hiperdex.com","Hiperdex"),
]

# index kotatsu enums by domain
kdom = {}
for e, k in ksrc.items():
    for d in k["domains"]:
        kdom.setdefault(d, []).append(e)

def reg(host):  # already reg-keys in data; passthrough
    return host

ok=miss_kotatsu=miss_mihon=mapped=0
rows=[]
seen=set()
for dom,label in POPULAR:
    if (dom,label) in seen: continue
    seen.add((dom,label))
    enums = kdom.get(dom, [])
    in_mihon = dom in mbd
    if not enums:
        # source may exist under a sibling domain; mark kotatsu-missing
        rows.append((label,dom,"-","kotatsu:no-enum", "mihon:yes" if in_mihon else "mihon:no"))
        miss_kotatsu+=1
        continue
    # are any of these enums mapped?
    mapped_enums=[e for e in enums if e in full]
    if mapped_enums:
        # show a representative mapping + whether target domain matches
        reps=set()
        for e in mapped_enums:
            r=full[e]; reps.add(f"{r['mihonName']}({r['mihonDomain']})")
        correct = any(full[e]["mihonDomain"]==dom for e in mapped_enums)
        rows.append((label,dom,",".join(sorted(set(enums))[:3]), "MAPPED "+("✓same-domain" if correct else "≈diff-domain"), "; ".join(sorted(reps)[:3])))
        mapped+=1; ok+= (1 if correct or in_mihon else 0)
    else:
        rows.append((label,dom,",".join(sorted(enums)[:3]), "UNMAPPED", "mihon:yes" if in_mihon else "mihon:no(not on keiyoushi)"))
        if in_mihon: miss_mihon+=1

print(f"{'LABEL':18}{'DOMAIN':26}{'STATUS':24}DETAIL")
print("-"*120)
for label,dom,enums,status,detail in rows:
    print(f"{label[:17]:18}{dom[:25]:26}{status:24}{detail[:60]}  [{enums}]")
print("-"*120)
total=len(seen)
print(f"popular checked={total}  mapped={mapped}  unmapped-but-on-mihon={miss_mihon}  no-kotatsu-enum={miss_kotatsu}")
