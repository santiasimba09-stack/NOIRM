# NOIRM UI kit

The owner's brief (2026-10-29): *smoother, minimalist, same palette.* Every NOIRM screen and the HUD
uses one small kit in `src/main/java/com/noirm/client/ui/` (client-only). Phase A moved the HUD,
`JutsuScreen`, `AllyScreen` and `CreatorScreen` onto it. Phase B (Shop, MissionBoard, Master,
BingoBook, Handbook, CrewOrders) should follow the same rules.

## Tokens (`Theme`)

All colours live in `Theme`. Don't add hex colours to a screen unless they mean something that only
that screen has (a nature's colour, a rank colour).

| Token | Use |
|---|---|
| `SCRIM` | the dim layer behind a screen |
| `SURFACE` | a screen's panel: flat ash-black |
| `RAISED` / `RAISED_HOVER` | cards, rows, inputs; under the mouse |
| `WELL` | sunken areas: bar tracks, slots, the 3D model view |
| `HAIRLINE` | the one 1-px line: panel edge, section rules, separators |
| `IRON` | a stronger line: scrollbar thumbs, focus |
| `TEXT` / `TEXT_DIM` / `TEXT_FAINT` | bone white body text / labels / hints and disabled |
| `SEAL` | the blood-red accent: active tab underline, the 忍 seal, primary actions |
| `RED` | warnings, damage, "no" |
| `GOLD` | highlights: rank, mastery, "you can do this now" |
| `GREEN` | owned, ready, done |
| `CHAKRA` / `CHAKRA_LIGHT` | chakra bars; the creator's accent |
| `NATURE`, `AMBER`, `HEALTH` | nature energy, posture, health |

Spacing: `S1..S6` = 2, 4, 6, 8, 12, 16 GUI pixels. `LINE` = 10 (one text line), `ROW` = 14 (a
clickable row). Colour maths: `Theme.mix`, `fade`, `alpha`, `darken`.

## Components (`Ui`, `TabStrip`, `HudKit`)

| Piece | Call | Look |
|---|---|---|
| Panel | `Ui.panel` | flat `SURFACE` + one hairline. No double frames, rivets, gradients or paper lines. |
| Section header | `Ui.section` | small UPPER-CASE label + a thin rule to the right |
| Button | `Ui.button(..., hover, enabled, accent, primary)` | flat; an accent underline grows from the centre on hover; primary = tinted + full underline |
| Toggle / lit pill | `Ui.toggle` | like a button, tinted while on |
| Tabs | `TabStrip.draw` + `at()` | text only; one underline slides (eases) to the active tab |
| Segmented control | `Ui.segment` (+ `segX/segW` for hit boxes) | equal parts, hairline separators, active = tint + underline |
| Slider | `Ui.slider` | 2-px track, accent fill, small bone knob |
| Progress / stat bar | `Ui.bar` | thin, corner pixels left out (reads rounded), optional light "ghost" trail after a drop |
| Card / list row | `Ui.row` | transparent → raised on hover; selected = tint + 1-px accent bar on the left |
| Chip | `Ui.chip` | small tag on a faint tint of its colour |
| Scrollbar | `Ui.scrollbar` | 1-px track, 2-px thumb, only when the list overflows |
| Text | `Ui.fit` / `Ui.text` / `Ui.wrap` / `Ui.truncated` | cut with "…"; if text is cut, show it whole as a tooltip |
| Icon + label | `Ui.iconLabel` | glyph, gap, label |
| Tooltips | implement `NoirmStyled` on the screen | `TooltipStyle` gives them the ash surface and an iron hairline |
| HUD row | `HudKit.barRow` | upper-case caption + number (fades) over a 3-px bar, no panel |

Screens draw flat (no text shadow). The HUD draws over the world, so it keeps text shadows but no boxes.

## Motion (`Anim`, `Smooth`, `HoverTracker`, `ScreenMotion`)

- **Time-based only.** Everything uses `Anim.now()` (milliseconds), never frame counts.
- **Open:** 170 ms ease-out cubic fade + 5 px rise. **Close:** 110 ms ease-in fade. A screen calls
  `motion.open()` once in `init()`, `motion.close(super::onClose)` in `onClose()`, and `motion.tick()`
  from `tick()` (`containerTick()` for container screens). Ignore clicks while `motion.closing()`.
- **Tabs:** `motion.tabChanged()` on a switch; draw the tab body between `beginContent` / `endContent`
  for a 140 ms cross-fade. Filters that change a list can use it too.
- **Hover:** `HoverTracker.get(key, hovered)` gives an eased 0..1 (55 ms time constant); mix colours
  and grow underlines with it.
- **Bars and counters:** one `Smooth` per bar, created once (`static final` or a field). `update(target)`
  eases toward the value; `ghost()` is the slow trail; `recency()` is 1 just after a change and fades
  out (2.2 s hold, 0.45 s fade): use it for "show the number when it changes".
- **No allocation per frame in hot paths** (the HUD): no new objects in `render` for animation state;
  cache translated labels (`HudLabels`).
- `GuiGraphics.setColor` alpha is how fades work: always pair `begin` with `end` (it resets the colour).

## Layout rules

- Work at GUI scale *auto* for 1280×720 (426×240 GUI px) and 1920×1080 (480×270). Measure text,
  never assume widths; keep 12 px padding inside panels.
- **HUD corners:** top-left = vitals (chakra, stamina, posture) and status lines; top-right = career
  and mission, below the effect icons and boss bars (`HudLayout.topRight`); top-centre = the target,
  below boss bars (`HudLayout.topCenter`); bottom-right = jutsu slots, right of the hunger/air rows
  (`HudLayout.bottomRightMinX`) and sized to the screen so nothing runs off the edge.
- **Hide what isn't relevant:** stats only in combat mode or for a moment when one grows; the clan
  for a few seconds after joining; eyes while active or in combat; the cloak while it matters.
- Fewer boxes: group with whitespace and a hairline, not frames.
