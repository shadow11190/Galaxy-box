"""Generate offline facts from cached public sources. Never runs during an Android build.

Usage: python tools/build_hardware_catalog.py PATH_TO_REFERENCES
No network access. Exact models are preserved; unknown specs are left empty.
"""
import csv, json, re, sys, hashlib, collections
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
REF=Path(sys.argv[1]); ASSETS=ROOT/'app/src/main/assets'; DATA=ROOT/'catalog'
DATA.mkdir(exist_ok=True)
REV='56a157866af6232edf646ae0f3e1b300e509c8e5'
GOOGLE='https://storage.googleapis.com/play_public/supported_devices.csv'
WIKI=f'https://github.com/LineageOS/lineage_wiki/blob/{REV}/_data/devices/'
CC='https://www.commoncriteriaportal.org/nfs/ccpfiles/files/epfiles/st_vid11664-st.pdf'

def scalar(v):
    v=v.strip()
    if len(v)>1 and v[0]==v[-1] and v[0] in "\"'": return v[1:-1].replace("''", "'")
    return v

def values(v):
    if v.startswith('[') and v.endswith(']'):
        return [scalar(x) for x in next(csv.reader([v[1:-1]], skipinitialspace=True))]
    return [scalar(v)] if v else []

def fields(text):
    # Only these scalar/list facts; never execute YAML tags, templates or instructions.
    result={}; lines=text.splitlines()
    for i,line in enumerate(lines):
        m=re.match(r'^(soc|models|vendor|codename|name|cpu|cpu_cores|cpu_freq|architecture|release|type):\s*(.*)$',line)
        if m:
            k,v=m.groups()
            if not v:
                seq=[]
                for extra in lines[i+1:]:
                    item=re.match(r'^\s+-\s+(.+)$',extra)
                    if not item: break
                    seq.append(scalar(item[1]))
                result[k]=seq
            else: result[k]=values(v) if k in ('models','soc','cpu') else scalar(v)
    return result

def clean_name(s):
    s=re.sub(r'Gen\s*(\d)',r'Gen \1',s)
    s=re.sub(r' (?:4G|5G)$','',s)
    return s.replace('Snapdragon S4 Plus','Snapdragon S4').strip()

processors=collections.defaultdict(lambda:{'aliases':set(),'sources':set(),'process':'','cpu':'','cpuCandidates':set(),'coreCandidates':set(),'groupCandidates':set(),'archCandidates':set()})
variants=set()
lineage_device_facts=set()
lineage_without_models=[]
PHONE_TYPES={'phone','foldable','phone (slider)'}
def release_year(value):
    if not isinstance(value,str):return None
    match=re.match(r'^(20\d\d)(?:\b|-)',value or '')
    return int(match[1]) if match and 2010 <= int(match[1]) <= 2026 else None
def processor(name,aliases,source,process='',cpu=''):
    name=clean_name(name)
    p=processors[name]; p['aliases'].update(a for a in aliases if a);p['sources'].add(source)
    if process:p['process']=process
    if cpu:p['cpu']=cpu

lineage_files=sorted((REF/'lineage_wiki/_data/devices').glob('*.yml'))
for f in lineage_files:
    d=fields(f.read_text(encoding='utf-8'))
    source=WIKI+f.name
    for soc in d.get('soc',[]):
        aliases=[soc]; name=soc
        m=re.fullmatch(r'Qualcomm ((?:SM|MSM|APQ|SDM)\d+[A-Za-z0-9-]*(?: Pro)?) (Snapdragon .+)',soc)
        if m: aliases.append(m[1].replace(' Pro','PRO')); name='Qualcomm '+m[2]
        m=re.fullmatch(r'Samsung Exynos (\w+)',soc)
        if m:aliases.extend(['Exynos'+m[1], 'universal'+m[1]])
        if soc=='Google Tensor GS101':name='Google Tensor';aliases+=['GS101','Google Tensor']
        if soc=='Google Tensor GS201':name='Google Tensor G2';aliases+=['GS201','Tensor G2']
        if soc.startswith('Google Tensor G'):aliases.append(soc.removeprefix('Google '))
        processor(name,aliases,source)
        cpu=d.get('cpu',[])
        if cpu and all(isinstance(c,str) and c for c in cpu):
            processors[clean_name(name)]['cpuCandidates'].add(' + '.join(cpu))
        cores=d.get('cpu_cores')
        if isinstance(cores,str) and cores.isdigit():processors[clean_name(name)]['coreCandidates'].add(cores)
        freq=d.get('cpu_freq')
        if isinstance(freq,str) and isinstance(cores,str) and cores.isdigit():
            parts=[re.fullmatch(r'(\d+)\s*[x×]\s*[\d.]+\s*[MG]Hz',x.strip()) for x in freq.split('+')]
            if len(parts)>1 and all(parts) and sum(int(x[1]) for x in parts)==int(cores):
                processors[clean_name(name)]['groupCandidates'].add(' + '.join(x[1] for x in parts))
        if d.get('architecture') in ('arm','arm64','x86','x86_64'):
            processors[clean_name(name)]['archCandidates'].add(d['architecture'])
        if all(isinstance(d.get(k),str) for k in ['vendor','name','codename']):
            if not d.get('models'):
                lineage_without_models.append((d['vendor'],d['codename'],d['name'],clean_name(name),d.get('type',''),release_year(d.get('release','')),source))
            for model in d.get('models',[]):
                variants.add((d['vendor'],model,d['codename'],d['name'],clean_name(name),'完整型號 / LineageOS 規格參考',source))
                lineage_device_facts.add((d['vendor'],model,d['codename'],d['name'],clean_name(name),d.get('type',''),release_year(d.get('release','')),source))

# Curated facts are separately reviewable, with a source per record, not code guesses.
with (DATA/'processors-curated.tsv').open(encoding='utf-8') as f:
    for r in csv.DictReader((x for x in f if not x.startswith('#')),delimiter='\t'):
        processor(r['name'],r['aliases'].split('|'),r['source'],r['process'],r['cpu'])

source_ids={s:str(i) for i,s in enumerate(sorted({s for p in processors.values() for s in p['sources']}))}
rows=[]
for name,p in sorted(processors.items()):
    if len(p['cpuCandidates'])==1:
        detail=next(iter(p['cpuCandidates']))
        if not p['cpu']:p['cpu']=detail
        elif p['cpu'] in ('Qualcomm Kryo','Qualcomm Oryon') and detail not in ('Kryo','Oryon'):
            p['cpu']+=' · '+detail
    vendor=next((v for v in ['Qualcomm','MediaTek','Samsung','Xiaomi','Google','HiSilicon','NVIDIA','Intel','Amlogic','TI'] if name.startswith(v)),name.split()[0])
    # Marketing names are also valid exact API reports. No arbitrary suffix removal.
    p['aliases'].add(name)
    rows.append('\t'.join([name,vendor,p['process'],p['cpu'],'|'.join(sorted(p['aliases'])),'|'.join(source_ids[s] for s in sorted(p['sources']))]))
header='# name\tvendor\tprocess\tcpu\taliases\tsourceIds\n'
(ASSETS/'processors.tsv').write_text(header+'\n'.join(rows)+'\n',encoding='utf-8')
(ASSETS/'processor-sources.json').write_text(json.dumps({v:k for k,v in source_ids.items()},ensure_ascii=False,indent=2),encoding='utf-8')
# Compile the same facts for pure lookups, including use before Android Context exists.
def quoted(s):return json.dumps(s,ensure_ascii=False).replace('$','\\$')
generated='package dev.galaxy.toolbox.data\n\n/** Generated by tools/build_hardware_catalog.py; edit catalog/processors-curated.tsv. */\ninternal object ProcessorData {\n val rows = listOf(\n'+',\n'.join('  '+quoted(r) for r in rows)+'\n )\n val sources = mapOf(\n'+',\n'.join('  '+quoted(v)+' to '+quoted(k) for k,v in source_ids.items())+'\n )\n}\n'
(ROOT/'app/src/main/java/dev/galaxy/toolbox/data/ProcessorData.kt').write_text(generated,encoding='utf-8')

# Structured, reviewed CPU facts are distinct from runtime ABI/cpufreq/features.
cpu_specs={}
for name,p in processors.items():
    # Only unanimous reference facts. Do not promote per-device clock speeds to SoC maxima.
    layout=p['cpu'] if p['cpu'] not in ('Qualcomm Oryon','Qualcomm Kryo') else ''
    if len(p['groupCandidates'])==1 and len(p['coreCandidates'])==1:
        layout+='｜核心群組 '+next(iter(p['groupCandidates']))
    arch=next(iter(p['archCandidates'])) if len(p['archCandidates'])==1 else ''
    cpu_specs[name]={'name':name,'generation':'','layout':layout.strip('｜'),'cores':next(iter(p['coreCandidates'])) if len(p['coreCandidates'])==1 else '',
        'architecture':{'arm':'Arm（32 位元規格）','arm64':'Arm64（64 位元規格）','x86':'x86（32 位元規格）','x86_64':'x86-64（64 位元規格）'}.get(arch,''),
        'referenceClock':'','cache':'','source':'\n'.join(sorted(p['sources']))}
with (DATA/'cpu-details-curated.tsv').open(encoding='utf-8') as f:
    for r in csv.DictReader(f,delimiter='\t'):
        assert r['name'] in processors,r['name']
        for key,value in r.items():
            if value:cpu_specs[r['name']][key]=value.replace('|','\n')
cpu_cols=['name','generation','layout','cores','architecture','referenceClock','cache','source']
cpu_rows=['\t'.join(cpu_specs[name][key].replace('\n','|') for key in cpu_cols) for name in sorted(cpu_specs)]
(ASSETS/'cpu-details.tsv').write_text('\t'.join(cpu_cols)+'\n'+'\n'.join(cpu_rows)+'\n',encoding='utf-8')
(ROOT/'app/src/main/java/dev/galaxy/toolbox/data/CpuSpecData.kt').write_text('package dev.galaxy.toolbox.data\n\n/** Generated offline; edit catalog/cpu-details-curated.tsv. */\ninternal object CpuSpecData {\n val rows = listOf(\n'+',\n'.join('  '+quoted(r) for r in cpu_rows)+'\n )\n}\n',encoding='utf-8')
(DATA/'cpu-detail-coverage.json').write_text(json.dumps({'processorNames':len(cpu_specs),'reviewedDetailRecords':len(list(csv.DictReader((DATA/'cpu-details-curated.tsv').open(encoding='utf-8'),delimiter='\t'))),
    'withCpuDetail':sum(bool(r['layout']) for r in cpu_specs.values()),'withCoreCount':sum(bool(r['cores']) for r in cpu_specs.values()),
    'withGeneration':sum(bool(r['generation']) for r in cpu_specs.values()),'withReferenceClock':sum(bool(r['referenceClock']) for r in cpu_specs.values()),
    'limits':['Not all released chipsets are covered. Missing or conflicting facts stay empty.','Reference groups describe published configurations, not a measured mapping to Linux CPU indices.','Arm64/64-bit does not establish an Armv9 sub-version or enabled CPU instructions.']},ensure_ascii=False,indent=2),encoding='utf-8')

google_rows=list(csv.DictReader((REF/'supported_devices.csv').open(encoding='utf-16')))
devices=set()
for r in google_rows:
    brand,model,code,name=(r[k].strip() for k in ['Retail Branding','Model','Device','Marketing Name'])
    if brand and model and code and name and not any('\t' in v or '\n' in v for v in [brand,model,code,name]):
        devices.add((brand,model,code,name))
# The Play list has neither release date nor form factor. Keep all original rows
# available for lookup; classify only exact brand + full model matches backed by
# the dated LineageOS record. An unmatched row is unknown, never "a phone".
def model_key(value):return re.sub(r'/(?:DS|DSN)$','',value.strip().upper())
def brand_key(value):
    aliases={'asustek':'asus','asustek computer inc.':'asus','lge':'lg','lg electronics':'lg',
             'hmd global':'nokia','hmd global oy':'nokia','motorola mobility':'motorola'}
    value=value.strip().lower()
    return aliases.get(value,value)
# A source without explicit SKUs may still be joined to a Google SKU only when
# brand, code name, and marketing name all agree exactly. No name-only spread.
google_by_identity=collections.defaultdict(set)
for brand,model,code,name in devices:
    google_by_identity[(brand_key(brand),code.casefold(),name.casefold())].add((brand,model,code,name))
for brand,code,name,soc,kind,year,source in lineage_without_models:
    for google in google_by_identity[(brand_key(brand),code.casefold(),name.casefold())]:
        lineage_device_facts.add((*google,soc,kind,year,source))
        variants.add((*google,soc,'完整型號 / Google 名稱 × LineageOS 代號核對',source))
with (DATA/'phones-2018-curated.tsv').open(encoding='utf-8') as f:
    for row in csv.DictReader(f,delimiter='\t'):
        google=(row['brand'],row['model'],row['device'],row['name'])
        year=release_year(row['releaseYear'])
        if google not in devices or row['soc'] not in processors or year is None or year < 2018:
            raise ValueError(f'Unverified curated phone: {google}')
        lineage_device_facts.add((*google,row['soc'],'phone',year,row['source']))
        variants.add((*google,row['soc'],'完整型號 / 廠商公開規格核對',row['source']))
lineage_by_model=collections.defaultdict(set)
for brand,model,code,name,soc,kind,year,source in lineage_device_facts:
    lineage_by_model[(brand_key(brand),model_key(model))].add((kind,year,source))
classifications=[]
for brand,model,code,name in sorted(devices):
    candidates=lineage_by_model[(brand_key(brand),model_key(model))]
    kinds_and_years={(kind,year) for kind,year,_ in candidates}
    if len(kinds_and_years)==1:
        kind,year=next(iter(kinds_and_years))
        if kind and year:
            classifications.append((brand,model,code,kind,str(year),'|'.join(sorted(source for _,_,source in candidates))))
verified_phones=sorted((brand,model,code,name,soc,str(year),source)
    for brand,model,code,name,soc,kind,year,source in lineage_device_facts
    if kind in PHONE_TYPES and year is not None and year>=2018)
processor_usage=collections.defaultdict(lambda:{'years':set(),'sources':set()})
for brand,model,code,name,soc,year,source in verified_phones:
    processor_usage[soc]['years'].add(int(year))
    processor_usage[soc]['sources'].add(source)
(ASSETS/'processor-phone-usage-2018plus.tsv').write_text('# processor\tfirstVerifiedPhoneYear\tlastVerifiedPhoneYear\tsource\n'+
    '\n'.join('\t'.join([soc,str(min(fact['years'])),str(max(fact['years'])),'|'.join(sorted(fact['sources']))])
      for soc,fact in sorted(processor_usage.items()))+'\n',encoding='utf-8')
(ASSETS/'device-classifications.tsv').write_text('# brand\tmodel\tdevice\ttype\treleaseYear\tsource\n'+
    '\n'.join('\t'.join(row) for row in classifications)+'\n',encoding='utf-8')
(ASSETS/'verified-phones-2018plus.tsv').write_text('# brand\tmodel\tdevice\tname\tsoc\treleaseYear\tsource\n'+
    '\n'.join('\t'.join(row) for row in verified_phones)+'\n',encoding='utf-8')
(ASSETS/'android-device-names.tsv').write_text('# brand\tmodel\tdevice\tname\n'+'\n'.join('\t'.join(r) for r in sorted(devices))+'\n',encoding='utf-8')
(ASSETS/'samsung-device-names.tsv').write_text('# model\tdevice\tname\n'+'\n'.join('\t'.join(r[1:]) for r in sorted(devices) if r[0].lower()=='samsung')+'\n',encoding='utf-8')

with (DATA/'samsung-variants-curated.tsv').open(encoding='utf-8') as f:
    for r in csv.DictReader((x for x in f if not x.startswith('#')),delimiter='\t'):
        for model in r['models'].split('|'):
            # Join only an exact public SKU. This prevents accidental invented regional models.
            matching=[d for d in devices if d[0].lower()=='samsung' and d[1].upper()==model.upper()]
            for d in matching:variants.add((*d,r['soc'],r['market'],r['source']))
extra=DATA/'additional-variants-curated.tsv'
if extra.exists():
    for r in csv.DictReader(extra.open(encoding='utf-8'),delimiter='\t'):
        d=(r['brand'],r['model'],r['device'],r['name'])
        if d in devices:variants.add((*d,r['soc'],r['market'],r['source']))
(ASSETS/'device-variants.tsv').write_text('# brand\tmodel\tdevice\tname\tsoc\tmarket\tsource\n'+'\n'.join('\t'.join(r) for r in sorted(variants))+'\n',encoding='utf-8')

bybrand=collections.Counter(r[0] for r in devices)
coverage={'retrieved':'2026-10-20','processorNames':len(processors),'processorAliases':len({a for p in processors.values() for a in p['aliases']}),'deviceRows':len(devices),'brands':len(bybrand),'samsungRows':sum(n for b,n in bybrand.items() if b.lower()=='samsung'),'deviceSpecRows':len(variants),'samsungSpecRows':sum(r[0].lower()=='samsung' for r in variants),'googleSource':GOOGLE,'googleSha256':hashlib.sha256((REF/'supported_devices.csv').read_bytes()).hexdigest(),'lineageCommit':REV,'lineageFiles':len(lineage_files),'byBrand':dict(bybrand.most_common()),'limits':['Google list includes certified Android devices, not every released or uncertified phone.','Google marketing-name records do not contain processor, memory, radio or region specs.','LineageOS specifications cover listed supported models only; no chipset propagation to other regions.','A hardware model is not an active CSC or a current sales location.','Shared SoC part numbers retain all candidates until another actual chip report disambiguates.','Samsung CC tables contain some inconsistent model labels; only manually reviewed, Google-matching full SKUs are used.']}
coverage.update({'processorsWithProcess':sum(bool(p['process']) for p in processors.values()),
    'processorsWithCpu':sum(bool(p['cpu']) for p in processors.values()),
    'deviceModelsWithSpecs':len({(r[0],r[1]) for r in variants}),
    'verified2018PhoneRows':len(verified_phones),
    'verified2018PhoneModels':len({(r[0].lower(),r[1].upper()) for r in verified_phones}),
    'verified2018PhoneProcessors':len(processor_usage),
    'classifiedGoogleRows':len(classifications),
    'classifiedGoogle2018PhoneRows':sum(kind in PHONE_TYPES and int(year)>=2018 for _,_,_,kind,year,_ in classifications),
    'classifiedGooglePre2018Rows':sum(int(year)<2018 for _,_,_,_,year,_ in classifications),
    'classifiedGoogleOtherRows':sum(kind not in PHONE_TYPES for _,_,_,kind,_,_ in classifications)})
coverage['limits'].append('A 2018+ phone is counted only when a source provides both release year and phone form factor; all other Google rows remain unclassified, not deleted.')
phone_brands=collections.defaultdict(set)
for brand,model,code,name,soc,year,source in verified_phones:
    phone_brands[brand].add(model.upper())
(DATA/'phone-coverage-by-brand.tsv').write_text('brand\tverified2018PhoneModels\n'+
    '\n'.join(f'{brand}\t{len(models)}' for brand,models in sorted(phone_brands.items(),key=lambda item:(-len(item[1]),item[0])))+'\n',encoding='utf-8')
vendor_totals=collections.Counter(name.split(' ',1)[0] if name.split(' ',1)[0]!='HiSilicon' else 'HiSilicon' for name in processors)
vendor_verified=collections.Counter(soc.split(' ',1)[0] for soc in processor_usage)
(DATA/'processor-coverage-by-vendor.tsv').write_text('vendor\tallCatalogNames\tverified2018PhoneUse\n'+
    '\n'.join(f'{vendor}\t{count}\t{vendor_verified[vendor]}' for vendor,count in sorted(vendor_totals.items()))+'\n',encoding='utf-8')
(DATA/'processor-spec-gaps.tsv').write_text('# name\tmissing\tsources\n'+'\n'.join('\t'.join([name,
    '|'.join(k for k in ('process','cpu') if not p[k]),' | '.join(sorted(p['sources']))])
    for name,p in sorted(processors.items()) if not p['process'] or not p['cpu'])+'\n',encoding='utf-8')
(ASSETS/'hardware-catalog-coverage.json').write_text(json.dumps(coverage,ensure_ascii=False,indent=2),encoding='utf-8')
(DATA/'coverage.json').write_text(json.dumps(coverage,ensure_ascii=False,indent=2),encoding='utf-8')
(ASSETS/'device-catalog-source.json').write_text(json.dumps({'source':GOOGLE,'retrieved':coverage['retrieved'],'sha256':coverage['googleSha256'],'rows':coverage['deviceRows'],'samsungRows':coverage['samsungRows'],'description':'Exact brand/model/device marketing-name facts. No processor, memory, radio or current CSC is inferred from marketing name. Regional SKUs remain separate.'},ensure_ascii=False,indent=2),encoding='utf-8')
(ASSETS/'licenses/LineageOS-wiki-MIT.txt').write_bytes((REF/'lineage_wiki/licenses/LICENSE').read_bytes())
print(json.dumps({k:v for k,v in coverage.items() if k not in ['byBrand','limits']},indent=2))
