"""Patch only web integration onto the proven live client, preserving Lunita fixes."""
import struct, json, hashlib, zipfile
from pathlib import Path

def replace_utf8(data, replacements):
    count = struct.unpack_from('>H', data, 8)[0]
    offset, index = 10, 1
    output = bytearray(data[:10])
    changed = []
    sizes = {3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,17:4,18:4,19:2,20:2}
    while index < count:
        tag = data[offset]; output.append(tag); offset += 1
        if tag == 1:
            length = struct.unpack_from('>H',data,offset)[0]; offset += 2
            original = data[offset:offset+length]; offset += length
            replacement = replacements.get(original, original)
            output.extend(struct.pack('>H',len(replacement))); output.extend(replacement)
            if original != replacement: changed.append(original.decode())
        else:
            size = sizes[tag]; output.extend(data[offset:offset+size]); offset += size
            if tag in (5,6): index += 1
        index += 1
    output.extend(data[offset:])
    return bytes(output), changed

base = Path('build/lunaeternal-lunita-pose-fix-client.jar')
compiled = Path('mod/build/libs/lunaeternal-0.1.0.jar')
output = Path('build/lunaeternal-web-portal-client.jar')
classes = ['net/pokereport/luna/client/Enlaces.class', 'net/pokereport/luna/client/pokepad/Apps.class', 'net/pokereport/luna/pokepad/CatalogoPad.class']
urls = {
    b'https://pokereport.online/': b'https://pokereport.online/tienda/',
    b'https://wiki.pokereport.net/': b'https://pokereport.online/wiki/',
    b'https://discord.gg/pokereport': b'https://discord.gg/JsUTQWRH8H',
}
rewrites = {}
with zipfile.ZipFile(base) as old, zipfile.ZipFile(compiled) as new, zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as out:
    for item in old.infolist():
        data = new.read(item.filename) if item.filename in classes else old.read(item.filename)
        if item.filename in ['net/pokereport/luna/client/pokepad/PokePadScreen.class', 'net/pokereport/luna/client/mixin/MainMenuMixin.class']:
            data, changes = replace_utf8(data, urls)
            rewrites[item.filename] = changes
        if item.filename in ['assets/lunaeternal/lang/'+lang+'.json' for lang in ['es_es','es_mx','en_us']]:
            language = json.loads(data)
            source = json.loads(Path('mod/src/client/resources/'+item.filename).read_text(encoding='utf-8'))
            language['pokepad.lunaeternal.app.wiki.desc'] = source['pokepad.lunaeternal.app.wiki.desc']
            data = (json.dumps(language,ensure_ascii=False,indent=2)+'\n').encode()
        out.writestr(item, data)
with zipfile.ZipFile(base) as old, zipfile.ZipFile(output) as new:
    changed = [name for name in old.namelist() if old.read(name) != new.read(name)]
    assert set(changed) == set(classes) | set(rewrites) | {'assets/lunaeternal/lang/'+lang+'.json' for lang in ['es_es','es_mx','en_us']}
    assert new.read('assets/lunaeternal/bedrock/pokemon/posers/lunita.json') == old.read('assets/lunaeternal/bedrock/pokemon/posers/lunita.json')
    assert b'https://pokereport.online/wiki/' in new.read(classes[0])
    assert b'abrirWiki' in new.read(classes[1])
audit = {'base_sha256':hashlib.sha256(base.read_bytes()).hexdigest(),'sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'changed':changed,'url_rewrites':rewrites,'preserved':len(old.namelist())-len(changed)}
Path(str(output)+'.audit.json').write_text(json.dumps(audit,indent=2))
print(json.dumps(audit,indent=2))
