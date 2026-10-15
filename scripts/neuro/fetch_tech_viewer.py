"""Real tech videos for the tech-viewer replay fixture (#907).

Searches YouTube for phone, laptop and PC hardware subjects and writes the results to
notes/neuro/techviewer/watched_candidates.json. TechViewerFixtureTool then watches 150 of them
through the real engine and writes the brain and watch history a recording device is given.

    python3 -I scripts/neuro/fetch_tech_viewer.py notes/neuro/techviewer/watched_candidates.json
"""
import json, sys, urllib.request, time
out = sys.argv[1]
queries = ["pixel 10 pro review","galaxy s26 ultra review","iphone 17 pro review","oneplus 14 review","nothing phone 3 review",
 "best android phones 2026","snapdragon 8 elite gen 5","macbook pro m5 review","best laptops 2026","gaming pc build 2026",
 "rtx 5080 review","android 17 features","framework laptop review","best earbuds 2026","smartwatch review 2026",
 "linux laptop","tech news this week","phone camera comparison","foldable phone review","steam deck oled"]
def search(q):
    body = {"context":{"client":{"clientName":"WEB","clientVersion":"2.20251010.00.00","hl":"en","gl":"US"}},"query":q,"params":"EgIQAQ%3D%3D"}
    req = urllib.request.Request("https://www.youtube.com/youtubei/v1/search?prettyPrint=false", data=json.dumps(body).encode(), headers={"Content-Type":"application/json","User-Agent":"Mozilla/5.0"})
    return json.load(urllib.request.urlopen(req, timeout=20))
def walk(o, acc):
    if isinstance(o, dict):
        v = o.get("videoRenderer")
        if v and v.get("videoId"):
            try:
                title = "".join(r["text"] for r in v["title"]["runs"])
                ch = v["ownerText"]["runs"][0]
                cid = ch["navigationEndpoint"]["browseEndpoint"]["browseId"]
                dur = v.get("lengthText",{}).get("simpleText","0:00")
                secs = 0
                for p in dur.split(":"): secs = secs*60+int(p)
                views = v.get("viewCountText",{}).get("simpleText","0")
                views = int("".join(c for c in views if c.isdigit()) or 0)
                age = v.get("publishedTimeText",{}).get("simpleText","")
                acc.append(dict(id=v["videoId"], title=title, channelId=cid, channelName=ch["text"], duration=secs, views=views, uploadDate=age))
            except Exception: pass
        for x in o.values(): walk(x, acc)
    elif isinstance(o, list):
        for x in o: walk(x, acc)
allv = {}
for q in queries:
    try:
        acc=[]; walk(search(q), acc)
        for v in acc[:10]:
            if v["duration"] >= 120: allv.setdefault(v["id"], dict(v, query=q))
        print(q, len(acc), file=sys.stderr)
    except Exception as e: print(q, "ERR", e, file=sys.stderr)
    time.sleep(0.5)
json.dump(list(allv.values()), open(out,"w"), indent=1)
print(len(allv))
