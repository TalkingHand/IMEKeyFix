# IME Key Fix

Project Zomboid Build 42 mod for Windows players who type with a Korean, Chinese or Japanese
IME. With the IME in native mode (e.g. Hangul), the game's hotkeys do nothing: Windows hands
every key press to the IME, and the game only receives `VK_PROCESSKEY`, which GLFW drops.

This mod detaches the IME from the game window while you play, so hotkeys always work, and
attaches it again while a text box is being typed in (crafting and building search, chat,
sign text, any `ISTextEntryBox`). The IME keeps its mode, so if you left it in Hangul, a search
box takes Hangul right away; no extra 한/영 press.

- Client-only Java mod. Requires [ZombieBuddy](https://github.com/zed-0xff/ZombieBuddy).
- Windows only; does nothing on other systems.
- Only the game window is affected. Other programs keep their IME state.
- Safe to add to or remove from existing saves (it changes no save data).
- Checked by hand with the Windows Korean IME and the Microsoft Japanese IME. Chinese IMEs use the
  same Windows mechanism and should work too, but were not tried.
- Key rebinding in the options also works with the IME in native mode: the rebind dialog is not a
  text box, so it records the key itself (e.g. `Q`).
- The game cannot show IME composition text (the word being composed) inside its text boxes; text
  appears when it is committed. That is vanilla behaviour and unchanged by this mod.

## Install

1. Subscribe to ZombieBuddy and this mod, and enable both in the mod list.
2. Steam → Project Zomboid → Properties → Launch options: `-agentlib:zbNative --`
   (ZombieBuddy's Java loader; without it the mod does not load). Remove it again before joining
   multiplayer servers if you don't want Java mods there.
3. On the first start ZombieBuddy asks once to approve `IMEKeyFix.jar`.
4. Check `Zomboid/console.txt` for `[IMEKeyFix] loaded` and, once in game, `IME detached`.

Manual install: copy `Contents/mods/IMEKeyFix` into `%USERPROFILE%\Zomboid\mods\`.

Lua console (debug mode): `IMEKeyFix.status()`, `IMEKeyFix.setEnabled(false)` (vanilla behaviour
for this session), `IMEKeyFix.setEnabled(true)`.

## How it works

`Display.processMessages` (the window thread, right before `glfwPollEvents`) is patched to call
`ImmAssociateContextEx(hwnd, NULL, 0)` when no text box is being typed in, and
`ImmAssociateContextEx(hwnd, NULL, IACE_DEFAULT)` when one is (`Core.currentTextEntryBox` is
editable, focused and visible). It only calls IMM when that state changes. If a call fails it logs
`[IMEKeyFix] ERROR ...` once and stops switching for the session.

## Layout

| Path | What |
|---|---|
| `Contents/mods/IMEKeyFix/42/` | The mod: `mod.info`, `media/java/client/IMEKeyFix.jar` (built) |
| `java/src/main/java/talkinghand/imekeyfix/` | Java sources (`Ime` switching, `Patches`, Lua API) |
| `tools/build.sh` | Builds the jar (uses `../PZTestHarness/tools/javabuild.sh`) |
| `tests/IMEKeyFix_Test.lua`, `pzt.conf` | In-game test |

## Testing

```bash
pzt run
```

(or `bash ../PZTestHarness/pzt run`). The test focuses a plain text box, the crafting and building
window search boxes, hides a focused box and closes windows with a focused search box,
and checks after each step that the IME was attached or detached (`ImmGetContext` on the window).

Not covered by the test: the chat box (it only exists in multiplayer; it is an `ISTextEntryBox` like
the tested box), real key presses and real IME typing. Those were checked by hand (Korean and
Japanese IME: hotkeys in game, typing in the crafting and building search).

## License

All rights reserved. No license is granted for redistribution or modified versions.

## Support

If this mod helps you, you can support it via [PayPal](https://www.paypal.com/paypalme/TalkingHands619/3). It's optional; every feature stays free.
