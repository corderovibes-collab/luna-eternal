"""Orthographic renders of the exported geometry and actual UV atlas."""
import json, math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]
ART=ROOT/'arte/rotom'
geo=json.loads((ROOT/'mod/src/main/resources/assets/lunaeternal/geo/rotom_dex.geo.json').read_text())
atlas=Image.open(ART/'rotom_dex.png')
views=[('Frente',0,0),('Atras',180,0),('Izquierda',90,0),('Derecha',-90,0),('Tres cuartos',-32,-15),('Arriba',0,-90),('Abajo',0,90)]
sheet=Image.new('RGB',(1400,800),'#0A1421')
for vi,(name,yaw,pitch) in enumerate(views):
    a,b=map(math.radians,[yaw,pitch])
    rotation=np.array([[math.cos(a),0,math.sin(a)],[0,1,0],[-math.sin(a),0,math.cos(a)]])
    rotation=np.array([[1,0,0],[0,math.cos(b),-math.sin(b)],[0,math.sin(b),math.cos(b)]])@rotation
    img=Image.new('RGB',(350,400),'#0E1B2B'); draw=ImageDraw.Draw(img)
    faces=[]
    for bone in geo['minecraft:geometry'][0]['bones']:
        for c in bone.get('cubes',[]):
            x,y,z=c['origin']; w,h,d=c['size']
            points=np.array([[x,y,z],[x+w,y,z],[x+w,y+h,z],[x,y+h,z],
                             [x,y,z+d],[x+w,y,z+d],[x+w,y+h,z+d],[x,y+h,z+d]])
            points[:,1]-=9
            points=points@rotation.T
            for side,indices in [('north',[3,2,1,0]),('south',[6,7,4,5]),('west',[7,3,0,4]),
                                 ('east',[2,6,5,1]),('up',[7,6,2,3]),('down',[0,1,5,4])]:
                p=points[indices]; normal=np.cross(p[1]-p[0],p[2]-p[0])
                if normal[2]>=0: continue
                xy=[(175+float(v[0])*16,205-float(v[1])*16) for v in p]
                faces.append((float(p[:,2].mean()),xy,c['uv'][side],side))
    for depth,xy,uv,side in sorted(faces,reverse=True,key=lambda f:f[0]):
        u,v=uv['uv']; tw,th=uv['uv_size']
        tile=atlas.crop((u,v,u+tw,v+th)).convert('RGB')
        source=[(0,0),(tw,0),(tw,th),(0,th)]
        matrix=[]; values=[]
        for (px,py),(sx,sy) in zip(xy,source):
            matrix.extend([[px,py,1,0,0,0,-sx*px,-sx*py],[0,0,0,px,py,1,-sy*px,-sy*py]])
            values.extend([sx,sy])
        try: coeff=np.linalg.solve(matrix,values)
        except np.linalg.LinAlgError: continue
        warped=tile.transform(img.size,Image.Transform.PERSPECTIVE,coeff,Image.Resampling.NEAREST)
        mask=Image.new('L',img.size); ImageDraw.Draw(mask).polygon(xy,fill=255)
        img.paste(warped,(0,0),mask)
        draw.line(xy+[xy[0]],fill='#592830',width=1)
    draw.text((15,15),name,fill='#85EBFF')
    img.save(ART/(name.replace(' ','_')+'.png'))
    sheet.paste(img,((vi%4)*350,(vi//4)*400))
sheet.save(ART/'vistas.png')
