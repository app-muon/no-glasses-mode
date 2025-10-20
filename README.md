# NoGlassesMode

**NoGlassesMode** is a small Android utility that adds a Quick Settings tile to instantly toggle between your normal text size and a larger, easier-to-read size.  
It is designed for users who occasionally need bigger text, such as when reading without glasses.

---

## Features
- One-tap Quick Settings tile to switch between font scales  
- Customisable “big” text size using a simple slider  
- Works across all apps and system UI  
- Minimal permissions (only requires system write access to adjust font scale)  
- Optional About screen with links and credits  

---

## How it works
The app changes the system `FONT_SCALE` setting.  
When the tile is tapped:
1. The current scale is saved as “normal”.
2. The system font scale is increased by your chosen percentage.
3. Tapping again restores the saved normal scale.

---

## Requirements
- Android 8.0 (API 26) or later  
- “Modify system settings” permission enabled (the app guides you to grant it)

---

## Setup
1. Install the app.  
2. Open it once to choose your preferred larger text size.  
3. Grant the permission when prompted.  
4. Add the **No Glasses** tile:
   - Pull down Quick Settings  
   - Tap the pencil / “Edit tiles” icon  
   - Drag **No Glasses** into the active area  

---

## Privacy
No data is collected or shared.  
The app only changes a local system font-size value on your device.

---

## License
MIT License  
Copyright © 2025
