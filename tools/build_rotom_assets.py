"""Reproducible voxel model, UV atlas, editable Blockbench rig and Gecko animations."""
import base64
import json
import uuid
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'mod/src/main/resources/assets/lunaeternal'
ART = ROOT / 'arte/rotom'
ART.mkdir(parents=True, exist_ok=True)
COLORS = ['#F04436', '#A71D32', '#FF7750', '#242B3B', '#57DFF5', '#2175D2', '#F4FFFF', '#FFE17B']
atlas = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
draw = ImageDraw.Draw(atlas)
for index, color in enumerate(COLORS):
    x = index*16
    draw.rectangle((x, 0, x+15, 31), fill=color)
    # Pixel bevels, deliberate low frequency details rather than random noise.
    draw.line((x, 0, x+15, 0), fill='#FFFFFF', width=1)
    draw.line((x, 30, x+15, 30), fill='#152137', width=1)

def face(x, y, expression):
    draw.rectangle((x,y,x+31,y+31), fill='#1267B5')
    draw.rectangle((x+2,y+2,x+29,y+29), fill='#22ABE3')
    draw.rectangle((x+3,y+3,x+28,y+6), fill='#46D7F6')
    if expression == 'scan':
        for r in (5,10): draw.ellipse((x+16-r,y+16-r,x+16+r,y+16+r), outline='#A1F8FF', width=2)
        draw.line((x+3,y+16,x+28,y+16), fill='#F4FFFF')
        return
    for ex in (8,23):
        if expression == 'blink':
            draw.line((x+ex-3,y+14,x+ex+3,y+14),fill='#E5FFFF',width=2)
        elif expression == 'happy':
            draw.line([(x+ex-3,y+14),(x+ex,y+11),(x+ex+3,y+14)],fill='#FFFFFF',width=2)
        else:
            draw.rectangle((x+ex-3,y+9,x+ex+3,y+18), fill='#EFFFFF')
            draw.rectangle((x+ex-1,y+11,x+ex+1,y+17), fill='#1261A8')
            draw.point((x+ex,y+10),fill='white')
    if expression == 'alert':
        draw.line((x+4,y+8,x+12,y+11),fill='#193B71',width=2)
        draw.line((x+20,y+11,x+28,y+8),fill='#193B71',width=2)
    if expression == 'surprised':
        draw.rectangle((x+13,y+22,x+18,y+26), fill='#193B71')
    else:
        draw.polygon([(x+10,y+22),(x+22,y+22),(x+19,y+27),(x+13,y+27)], fill='#511D42')
        draw.line((x+11,y+22,x+21,y+22),fill='white',width=2)

for i, exp in enumerate(['idle','happy','surprised','attentive','scan','alert','blink']):
    face((i%4)*32,32+(i//4)*32,exp)

bones=[]
def bone(name, parent='root', pivot=(0,9,0)):
    b={'name':name,'pivot':list(pivot),'cubes':[]}
    if parent: b['parent']=parent
    bones.append(b)
    return b
root=bone('root',None,(0,0,0))
body=bone('body')
frame=bone('frame','body')
screen=bone('screen','body')
antenna=bone('antenna','body',(0,13,0))
left=bone('left_arm','body',(-5,8,0))
right=bone('right_arm','body',(5,8,0))
core=bone('bottom_core','body',(0,5,0))

def cube(b, name, pos, size, color, uv=None):
    tile = [color*16+2,2]
    faces={side:{'uv':tile,'uv_size':[12,26]} for side in ['north','south','east','west','up','down']}
    if uv: faces['north']={'uv':uv,'uv_size':[32,32]}
    b['cubes'].append({'origin':list(pos),'size':list(size),'uv':faces})

cube(body,'chassis',(-4.8,5,-1.3),(9.6,8,2.6),1)
cube(body,'back_plate',(-4.1,5.7,1.3),(8.2,6.6,.6),0)
cube(body,'rear_inset',(-3,7,1.9),(6,3.4,.2),3)
for x in (-3,0,3): cube(body,'rear_vent',(x-.7,7.5,2.1),(1.4,2.3,.1),1)
cube(screen,'glass',(-3.7,6,-1.85),(7.4,5.9,.35),4,[0,32])
cube(frame,'top',(-5,12,-1.9),(10,1.1,2.5),0)
cube(frame,'bottom',(-5,5,-1.9),(10,1,2.5),0)
for x in (-5,3.8):
    cube(frame,'side',(x,6,-1.9),(1.2,6,2.5),0)
    cube(frame,'corner',(x-.15,11,-2),(1.5,2.2,1),2)
    cube(frame,'bottomcorner',(x-.15,4.8,-2),(1.5,1.3,1),1)
cube(frame,'status',(-1.8,5.2,-2),(3.6,.3,.2),4)
for x in (-4.4,4.1): cube(frame,'screw',(x,5.4,-2.1),(.35,.35,.2),7)
for y,w,d in [(13,3.8,1.8),(14.5,2.8,1.5),(16,1.8,1.2),(17.5,.8,.8)]:
    cube(antenna,'antenna_step',(-w/2,y,-d/2),(w,1.6,d),0)
cube(antenna,'core',(-.35,14.5,-.85),(.7,3.3,.2),4)
for b,sign in [(left,-1),(right,1)]:
    cube(b,'hinge',(sign*5-.5,7.5,-.6),(1,1.2,1.2),3)
    cube(b,'link',(sign*6-.8,7.3,-.5),(1.6,1,.9),1)
    cube(b,'panel',(sign*7.7-1,7.8,-.65),(2,5,1.3),0)
    cube(b,'rim',(sign*7.7-.7,8.2,-.85),(1.4,4.2,.25),1)
    cube(b,'light',(sign*7.7-.4,9.2,-1),(.8,2.3,.2),4)
    cube(b,'tip',(sign*7.7-.85,12.8,-.6),(1.7,.7,1.2),2)
cube(core,'socket',(-2,4,-1),(4,1.2,2),3)
cube(core,'energy',(-1.1,2,-.65),(2.2,2,.9),4)
cube(core,'energy_tip',(-.45,.5,-.4),(.9,1.5,.65),6)
for x in (-1.8,1.4): cube(core,'spark',(x,2.5,-.3),(.4,1.5,.4),5)

def anim(duration, values, loop=False):
    return {'loop':loop,'animation_length':duration,'bones':values}
def keys(*values): return {str(t):v for t,v in values}
animations={
 'idle':anim(4,{'root':{'position':keys((0,[0,0,0]),(1,[0,.55,0]),(2,[0,0,0]),(3,[0,-.55,0]),(4,[0,0,0]))},
                   'body':{'rotation':keys((0,[0,0,-1]),(2,[0,0,1]),(4,[0,0,-1]))},
                   'left_arm':{'rotation':keys((0,[0,0,-8]),(2,[0,0,-13]),(4,[0,0,-8]))},
                   'right_arm':{'rotation':keys((0,[0,0,8]),(2,[0,0,13]),(4,[0,0,8]))},
                   'bottom_core':{'scale':keys((0,[1,1,1]),(2,[1,1.12,1]),(4,[1,1,1]))}},True),
 'blink':anim(.2,{'screen':{'scale':keys((0,[1,1,1]),(.1,[1,.08,1]),(.2,[1,1,1]))}}),
 'notice':anim(.8,{'body':{'rotation':keys((0,[0,0,0]),(.3,[-8,0,-6]),(.8,[0,0,0]))},
                      'left_arm':{'rotation':keys((0,[0,0,-8]),(.3,[0,0,-35]),(.8,[0,0,-8]))}}),
 'interact':anim(.6,{'body':{'rotation':keys((0,[0,0,0]),(.25,[12,0,0]),(.6,[0,0,0]))},
                        'left_arm':{'rotation':keys((0,[0,0,-8]),(.3,[0,0,-38]),(.6,[0,0,-20]))},
                        'right_arm':{'rotation':keys((0,[0,0,8]),(.3,[0,0,38]),(.6,[0,0,20]))}}),
 'scan':anim(1,{'antenna':{'rotation':keys((0,[0,0,0]),(.2,[0,12,0]),(.4,[0,-12,0]),(.6,[0,8,0]),(1,[0,0,0]))},
                    'left_arm':{'rotation':keys((0,[0,0,-20]),(.3,[0,-20,-45]),(.7,[0,20,-45]),(1,[0,0,-8]))},
                    'right_arm':{'rotation':keys((0,[0,0,20]),(.3,[0,20,45]),(.7,[0,-20,45]),(1,[0,0,8]))},
                    'bottom_core':{'scale':keys((0,[1,1,1]),(.5,[1.2,1.6,1.2]),(1,[1,1,1]))}}),
 'happy':anim(.8,{'body':{'rotation':keys((0,[0,0,0]),(.2,[0,0,9]),(.4,[0,0,-9]),(.6,[0,0,5]),(.8,[0,0,0]))}})
}
def dump(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,indent=2),encoding='utf-8')
geo={'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.rotom_dex',
     'texture_width':128,'texture_height':128,'visible_bounds_width':2,'visible_bounds_height':2,
     'visible_bounds_offset':[0,.65,0]},'bones':bones}]}
dump(ASSETS/'geo/rotom_dex.geo.json',geo)
dump(ASSETS/'animations/rotom_dex.animation.json',{'format_version':'1.8.0','animations':{'animation.rotom.'+k:v for k,v in animations.items()}})
texture=ASSETS/'textures/entity/rotom_dex.png'
texture.parent.mkdir(parents=True,exist_ok=True)
atlas.save(texture)
atlas.save(ART/'rotom_dex.png')
for i in range(7):
    variant = atlas.copy()
    variant.paste(atlas.crop(((i%4)*32,32+(i//4)*32,(i%4)*32+32,64+(i//4)*32)), (0,32))
    variant.save(texture.with_name(f'rotom_dex_{i}.png'))
    glow=Image.new('RGBA',(128,128),(0,0,0,0))
    for rect in [(64,0,80,32),(96,0,112,32),(0,32,32,64)]:
        glow.paste(variant.crop(rect),rect[:2])
    glow.save(texture.with_name(f'rotom_dex_{i}_glowmask.png'))

# Export the same cuboids and parent pivots, not a flattened model.
ids={b['name']:str(uuid.uuid5(uuid.NAMESPACE_DNS,'rotom.'+b['name'])) for b in bones}
elements=[]; groups=[]
for b in bones:
    children=[]
    for i,c in enumerate(b['cubes']):
        uid=str(uuid.uuid5(uuid.NAMESPACE_DNS,'rotom.'+b['name']+str(i)))
        children.append(uid)
        faces={side:{'uv':v['uv']+[v['uv'][0]+v['uv_size'][0],v['uv'][1]+v['uv_size'][1]],'texture':0} for side,v in c['uv'].items()}
        elements.append({'uuid':uid,'name':b['name']+'_'+str(i),'type':'cube','from':c['origin'],
            'to':[a+s for a,s in zip(c['origin'],c['size'])],'origin':b['pivot'],'faces':faces,'box_uv':False})
    groups.append({'name':b['name'],'uuid':ids[b['name']],'origin':b['pivot'],'children':children,'export':True,'isOpen':True})
byname={g['name']:g for g in groups}
for b in bones:
    if b.get('parent'): byname[b['parent']]['children'].append(byname[b['name']])
bb_anims=[]
for name,a in animations.items():
    animators={}
    for b,channels in a['bones'].items():
        kfs=[]
        for channel,points in channels.items():
            for t,v in points.items():
                kfs.append({'channel':channel,'time':float(t),'data_points':[dict(zip(['x','y','z'],map(str,v)))],
                            'interpolation':'linear','uuid':str(uuid.uuid4())})
        animators[ids[b]]={'name':b,'type':'bone','keyframes':kfs}
    bb_anims.append({'name':'animation.rotom.'+name,'length':a['animation_length'],'loop':'loop' if a['loop'] else 'once',
                     'uuid':str(uuid.uuid4()),'animators':animators})
dump(ART/'rotom_dex.bbmodel',{'meta':{'format_version':'4.10','model_format':'geckolib_model','box_uv':False},
    'name':'Rotom Dex','model_identifier':'rotom_dex','resolution':{'width':128,'height':128},
    'elements':elements,'outliner':[byname['root']],'animations':bb_anims,
    'textures':[{'name':'rotom_dex.png','id':'0','width':128,'height':128,'uv_width':128,'uv_height':128,
                 'source':'data:image/png;base64,'+base64.b64encode(texture.read_bytes()).decode()}]})
assert len(elements)>35 and len(animations)==6
print(f'Rotom: {len(elements)} cubes, {len(bones)} bones, 6 animations, 128px atlas')
