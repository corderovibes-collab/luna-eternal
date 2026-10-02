"""Build Lunita's species and client resources from the installed Cobblemon version."""
import copy
import json
import shutil
import sys
import zipfile
from lunita_uv import compile_atlas
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / 'mod/src/main/resources'
ASSETS = RES / 'assets/lunaeternal'
CLIENT = ROOT / 'mod/src/client/resources/assets/lunaeternal'
jar = Path(sys.argv[1])
with zipfile.ZipFile(jar) as source:
    eevee = json.loads(source.read('data/cobblemon/species/generation1/eevee.json'))
    sylveon = json.loads(source.read('data/cobblemon/species/generation6/sylveon.json'))
    poser = json.loads(source.read('assets/cobblemon/bedrock/pokemon/posers/0133_eevee/eevee.json'))
    native_animations = json.loads(source.read('assets/cobblemon/bedrock/pokemon/animations/0133_eevee/eevee.animation.json'))

def write(relative, value):
    target = RES / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

species = copy.deepcopy(eevee)
species.update(name='Lunita', nationalPokedexNumber=2001, primaryType='fairy', secondaryType='psychic',
               maleRatio=0, pokedex=['lunaeternal.species.lunita.desc'],
               labels=['custom', 'lunita_memorial', 'admin_only'],
               baseStats=sylveon['baseStats'], evYield=sylveon['evYield'],
               baseExperienceYield=sylveon['baseExperienceYield'],
               baseFriendship=255, eggGroups=['undiscovered'], evolutions=[], forms=[],
               drops={'amount': 0, 'entries': []})
species.pop('preEvolution', None)
species['moves'] = list(dict.fromkeys(eevee['moves'] + [
    '1:disarmingvoice', '5:fairywind', '10:confusion', '15:charm', '20:drainingkiss',
    '25:wish', '30:psybeam', '35:calmmind', '40:psychic', '45:dazzlinggleam',
    '50:moonblast', '55:lifedew', '60:healingwish',
    'tm:psychic', 'tm:psyshock', 'tm:dazzlinggleam', 'tm:drainingkiss',
    'tm:reflect', 'tm:lightscreen', 'tm:mistyterrain',
]))
# No spawn pool is produced. Undiscovered also closes the Ditto breeding route.
write('data/lunaeternal/species/custom/lunita.json', species)
write('data/lunaeternal/dex_entries/pokemon/lunita.json', {
    'id': 'lunaeternal:lunita', 'speciesId': 'lunaeternal:lunita',
    'displayAspects': [], 'conditionAspects': [],
    'forms': [{'displayForm': 'Normal', 'unlockForms': ['Normal']}], 'variations': []
})
write('data/lunaeternal/dexes/lunita.json', {
    'type': 'cobblemon:simple_pokedex_def', 'id': 'lunaeternal:lunita',
    'sortOrder': 12, 'entries': ['lunaeternal:lunita']
})

animations = {}
for name, animation in native_animations['animations'].items():
    animations[name.replace('animation.eevee.', 'animation.lunita.')] = copy.deepcopy(animation)
guardian = json.loads((ASSETS / 'animations/lunita.animation.json').read_text())['animations']
for name in ['idle', 'greet']:
    animations[f'animation.lunita.{name}'] = copy.deepcopy(guardian[f'animation.lunita.{name}'])
# Combat animation additions accompany the standard Cobblemon move effects.
for name, bone, channel, keys in [
    ('physical', 'body', 'position', {'0': [0, 0, 0], '0.18': [0, 0, -1.5], '0.45': [0, 0, 0]}),
    ('special', 'head', 'rotation', {'0': [0, 0, 0], '0.18': [-12, 0, 0], '0.55': [0, 0, 0]}),
    ('status', 'tail1', 'rotation', {'0': [0, 0, 0], '0.25': [0, 18, 0], '0.5': [0, -18, 0], '0.8': [0, 0, 0]}),
    ('recoil', 'body', 'position', {'0': [0, 0, 0], '0.12': [0, 0, 1], '0.35': [0, 0, 0]}),
]:
    animations[f'animation.lunita.{name}'] = {
        'animation_length': float(list(keys)[-1]), 'bones': {bone: {channel: keys}}
    }
animations['animation.lunita.sleep'] = {
    'loop': True,
    'bones': {
        **copy.deepcopy(animations['animation.lunita.ground_idle']['bones']),
        'body': {'position': [0, -1.2, 0]},
        'eyelid_left': {'position': [0, 0, -0.1]},
        'eyelid_right': {'position': [0, 0, -0.1]},
    }
}
write('assets/lunaeternal/bedrock/pokemon/animations/lunita/lunita.animation.json',
      {'format_version': '1.8.0', 'animations': animations})
for pose in poser['poses'].values():
    pose['animations'] = [a.replace("'eevee'", "'lunita'").replace("q.look('head'", "q.look('head_ai'") for a in pose['animations']]
    pose['quirks'] = [q.replace("'eevee'", "'lunita'") for q in pose['quirks']]
poser['rootBone'] = 'Lunita'
poser['animations'] = {
    'cry': "q.bedrock_stateful('lunita', 'cry')",
    'recoil': "q.bedrock_stateful('lunita', 'recoil')",
    **{name: f"q.bedrock_primary('lunita', '{name}', q.curve('symmetrical_wide'))" for name in ['physical', 'special', 'status']}
}
poser['poses']['standing']['animations'][-1] = "q.bedrock('lunita', 'idle')"
for shoulder in ['shoulder_left', 'shoulder_right']:
    # Root-level details must move with the complete Pokémon, not only its body.
    poser['poses'][shoulder]['transformedParts'][0]['part'] = '__root'
poser['poses']['sleep'] = {'poseTypes': ['SLEEP'], 'animations': ["q.bedrock('lunita', 'sleep')"]}
write('assets/lunaeternal/bedrock/pokemon/posers/lunita.json', poser)
write('assets/lunaeternal/bedrock/pokemon/resolvers/lunita/0_lunita_base.json', {
    'species': 'lunaeternal:lunita', 'order': 0, 'variations': [{
        'aspects': [], 'poser': 'lunaeternal:lunita', 'model': 'lunaeternal:lunita.geo',
        'texture': 'lunaeternal:textures/pokemon/lunita/lunita.png', 'layers': []
    }]
})
for source, target in [('geo/lunita.geo.json', 'bedrock/pokemon/models/lunita/lunita.geo.json'),
                       ('textures/entity/lunita.png', 'textures/pokemon/lunita/lunita.png')]:
    destination = ASSETS / target
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ASSETS / source, destination)
compiled_geometry = compile_atlas(json.loads((ASSETS / 'geo/lunita.geo.json').read_text()),
    ASSETS / 'textures/entity/lunita.png', ASSETS / 'textures/pokemon/lunita/lunita.png')
write('assets/lunaeternal/bedrock/pokemon/models/lunita/lunita.geo.json', compiled_geometry)

descriptions = {
    'es_es': 'Lunita es una compañera de tipo Hada y Psíquico, tan tierna que todos se derriten de amor con tan solo verla. Su mirada guarda la luz de los recuerdos felices. Le encanta descansar en el hombro de quien ama y acompañarlo en cada aventura. El cariño que dejó sigue brillando para siempre.',
    'en_us': 'Lunita is a Fairy and Psychic companion so sweet that hearts melt at the mere sight of her. Her gentle eyes hold the light of happy memories. She loves resting on the shoulder of someone she loves and sharing every adventure. The love she left behind shines forever.',
}
for language, description in descriptions.items():
    file = CLIENT / f'lang/{language}.json'
    values = json.loads(file.read_text(encoding='utf-8')) if file.exists() else {}
    values.update({'lunaeternal.species.lunita.name': 'Lunita', 'lunaeternal.species.lunita.desc': description})
    values['cobblemon.ui.pokedex.region.lunita'] = 'En memoria de Lunita' if language == 'es_es' else 'In Memory of Lunita'
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text(json.dumps(values, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
# Latin American clients also use the memorial text.
file = CLIENT / 'lang/es_mx.json'
values = json.loads(file.read_text(encoding='utf-8')) if file.exists() else {}
values.update({'lunaeternal.species.lunita.name': 'Lunita', 'lunaeternal.species.lunita.desc': descriptions['es_es']})
values['cobblemon.ui.pokedex.region.lunita'] = 'En memoria de Lunita'
file.parent.mkdir(parents=True, exist_ok=True)
file.write_text(json.dumps(values, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print('Built Lunita: Fairy/Psychic, Eevee abilities/learnset/cry/shoulders, 525 base stats (Sylveon), admin-only acquisition.')
