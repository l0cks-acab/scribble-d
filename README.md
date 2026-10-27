# Scribble'd

NeoForge 1.21.1 mod: whiteboards you can actually draw on.

- **Whiteboards** go on any wall (or the floor, facing you). Place them next to each other to build one big board, up to 8x8 blocks.
- **Draw screen**: right-click with an empty hand. 64x64 pixels per block, 16 dye colours, eraser, and an RGB picker box (colour square, hue bar, hex input) for any colour, brush sizes 1/2/3/5/8. Fill tool (paint bucket) floods across the whole board. Right-drag erases, right-click fill erases. Undo, Clear.
- **Markers**: hold right-click with a marker and draw straight on the board in the world. Sneak for a fine tip. The **Board Eraser** wipes the same way.
- **Undo**: `Z` in the world (rebindable), `Ctrl+Z` or the Undo button in the screen. Last 30 strokes.
- Strokes sync live to every player and save with the world. A broken board keeps its drawing on the item.
- Glow ink sac makes a board full-bright, ink sac turns it back.

## Recipes
- Whiteboard x3: 3 iron nuggets over 6 white concrete.
- Marker: stick + iron nugget + any dye (shapeless).
- Board Eraser: stick + white wool (shapeless).

## Marker model
The 3D marker was modeled, then converted: `tools/marker_profile.py` measures the mesh's radius and colour along its length into `tools/marker_profile.json`, and `tools/marker_model.py` turns that into crossed cuboids (tip and cap tinted per colour).
