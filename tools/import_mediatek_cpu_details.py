"""Offline import of the reviewed official CPU Specifications section; no network or build hook.
Usage: python tools/import_mediatek_cpu_details.py PATH_TO_CACHED_SOC_PAGES
The cache review.json pins URL and content digest. Run the catalog generator afterwards.
"""
from pathlib import Path
from html.parser import HTMLParser
import csv, hashlib, json, re, sys
P=Path(__file__).resolve().parents[1]; cache=Path(sys.argv[1])
class Text(HTMLParser):
    def __init__(self):super().__init__();self.skip=0;self.parts=[]
    def handle_starttag(self,t,a):
        if t in ('script','style'):self.skip+=1
    def handle_endtag(self,t):
        if t in ('script','style'):self.skip=max(0,self.skip-1)
    def handle_data(self,d):
        if not self.skip:self.parts.append(d)
path=P/'catalog/cpu-details-curated.tsv'
rows={r['name']:r for r in csv.DictReader(path.open(encoding='utf-8'),delimiter='\t')}
cols=['name','generation','layout','cores','architecture','referenceClock','cache','source']; reviewed=[]
known={r['name'] for r in csv.DictReader((P/'catalog/processors-curated.tsv').open(encoding='utf-8'),delimiter='\t')}
for r in json.loads((cache/'review.json').read_text(encoding='utf-8')):
    if 'file' not in r or r['name'] not in known:continue
    data=(cache/r['file']).read_bytes();assert hashlib.sha256(data).hexdigest()==r['sha256']
    parser=Text();parser.feed(data.decode('utf-8'));text=re.sub(r'\s+',' ',' '.join(parser.parts))
    section=text.rpartition('Specifications')[2].split('Memory')[0].split('GPU')[0]
    if r['name'].removeprefix('MediaTek ') not in re.sub(r'\s+',' ',r['title']):continue
    count=re.search(r'Cores (?:Octa|Quad) \((\d+)\)',section)
    if not count:continue
    groups=re.findall(r'(\d+)\s*[xX]\s+Arm (Cortex-[AX]\d+|C1-(?:Ultra|Premium|Pro))',section)
    if groups and sum(int(n) for n,_ in groups)==int(count[1]):layout='|'.join(n+' × '+cpu for n,cpu in groups)
    else:layout=' + '.join(dict.fromkeys(re.findall(r'Cortex-[AX]\d+|ARM-A\d+',section)))
    caches=re.findall(r'(\d+(?:\.\d+)?\s*[MK]B)\s+(L3)(?: cache| Cache)?',section)
    cache_text='|'.join(level+'：'+size for size,level in caches)
    slc=re.search(r'(\d+(?:\.\d+)?\s*[MK]B) SLC',section)
    if slc:cache_text+=('| ' if cache_text else '')+'系統級 SLC：'+slc[1]
    maximum=re.search(r'Max CPU Frequency (?:Up to )?(\d+(?:\.\d+)?\s*GHz)',section)
    record=dict(zip(cols,[r['name'],'',layout,count[1],'64 位元（官方規格）' if '64-bit' in section else '',
        '最高 '+maximum[1] if maximum else '',cache_text,r['source']]))
    rows[r['name']]=record
    reviewed.append({'name':r['name'],'source':r['source'],'sha256':r['sha256'],'section':section,'fields':record})
with path.open('w',encoding='utf-8',newline='') as out:
    w=csv.DictWriter(out,fieldnames=cols,delimiter='\t',lineterminator='\n');w.writeheader();w.writerows(sorted(rows.values(),key=lambda r:r['name']))
(P/'catalog/mediatek-cpu-detail-review.json').write_text(json.dumps({'reviewed':'2026-10-20','records':reviewed},ensure_ascii=False,indent=2),encoding='utf-8')
print('Detailed records:',len(rows),'reviewed MediaTek products:',len({r['name'] for r in reviewed}))
