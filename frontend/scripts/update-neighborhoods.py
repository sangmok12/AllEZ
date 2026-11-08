"""Refresh the bundled native Albamon codes; fail without replacing data on partial errors."""
import concurrent.futures
import datetime
import json
from pathlib import Path
import re
import urllib.request

root = Path(__file__).resolve().parents[1]
regions = json.loads((root / 'src/regions.json').read_text())
ids = {area['id']: area['jobKoreaCodes'][0] for region in regions for area in region['areas']}
ids['1000'] = '1000'  # Sejong has no district level.

def load(entry):
    region_id, code = entry
    url = 'https://api-code.albamon.com/codes/areas/korean/dongs/gucode?code=' + code
    with urllib.request.urlopen(url, timeout=20) as response:
        data = json.load(response)
    assert isinstance(data, list), region_id
    items = [{'id': item['code'], 'name': item['name']} for item in data]
    assert all(re.fullmatch('[A-Z0-9]{8}', item['id']) and item['id'].startswith(code)
               and item['name'].strip() and len(item['name']) <= 40 for item in items), region_id
    assert len({item['id'] for item in items}) == len(items), region_id
    return region_id, items

with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
    result = dict(executor.map(load, ids.items()))
output = {'updatedAt': datetime.date.today().isoformat(), 'regions': result}
payload = json.dumps(output, ensure_ascii=False, separators=(',', ':')) + '\n'
for target in [root / 'src/neighborhoods.json', root.parent / 'backend/src/main/resources/part-time/neighborhoods.json']:
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(payload)
print(f'Updated {len(result)} districts, {sum(map(len, result.values()))} neighborhoods')
