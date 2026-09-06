from PIL import Image
import os

files = {
    '1vs1': 'C:/Users/JUAN/.gemini/antigravity/brain/bd705599-0e86-4654-9e4f-20af6bf25d04/.user_uploaded/media_1788710347739.jpg',
    '2vs2': 'C:/Users/JUAN/.gemini/antigravity/brain/bd705599-0e86-4654-9e4f-20af6bf25d04/.user_uploaded/media_1788710347721.jpg',
    'aleatorio': 'C:/Users/JUAN/.gemini/antigravity/brain/bd705599-0e86-4654-9e4f-20af6bf25d04/.user_uploaded/media_1788710347732.jpg'
}

out_dir = 'mod/src/client/resources/assets/lunaeternal/textures/gui/pokepad'
os.makedirs(out_dir, exist_ok=True)

for name, path in files.items():
    img = Image.open(path)
    # Resize to 256x256 (textures in Minecraft work best as power of 2, even if the aspect ratio gets squashed, we render it properly)
    # Let's crop it to 256x256 to avoid squashing. Or just pad it.
    img = img.resize((256, 192), Image.Resampling.LANCZOS)
    bg = Image.new('RGBA', (256, 256), (0,0,0,0))
    bg.paste(img, (0, 32))
    out_path = os.path.join(out_dir, f'torre_modo_{name}.png')
    bg.save(out_path, format='PNG')
    print(f'Saved {out_path}')
