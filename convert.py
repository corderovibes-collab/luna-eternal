import sys
from PIL import Image

path = sys.argv[1]
out_path = sys.argv[2]
img = Image.open(path).convert("RGBA")
# Remove dark background roughly
data = img.getdata()
new_data = []
for item in data:
    # If pixel is very dark grey/black, make it transparent
    if item[0] < 30 and item[1] < 30 and item[2] < 30:
        new_data.append((255, 255, 255, 0))
    else:
        new_data.append(item)
img.putdata(new_data)
# Resize to 64x64 if needed, though pokepad might scale
img = img.resize((64, 64), Image.Resampling.LANCZOS)
img.save(out_path, "PNG")