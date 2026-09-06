Add-Type -AssemblyName System.Drawing
$path = 'C:\Users\JUAN\.gemini\antigravity\brain\bd705599-0e86-4654-9e4f-20af6bf25d04\torre_batalla_icon_1788705667152.jpg'
$out = 'mod\src\main\resources\assets\luna\textures\gui\pokepad\iconos\torre_batalla.png'
$img = [System.Drawing.Image]::FromFile($path)
$bmp = New-Object System.Drawing.Bitmap($img)
$img.Dispose()
$bmp.MakeTransparent([System.Drawing.Color]::FromArgb(255, 23, 27, 31)) # approx background color, wait, better just let the PokePad handle transparency or I can just save it as PNG
$resized = New-Object System.Drawing.Bitmap($bmp, 64, 64)
$bmp.Dispose()
$resized.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$resized.Dispose()