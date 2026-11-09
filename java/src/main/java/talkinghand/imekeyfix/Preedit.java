package talkinghand.imekeyfix;

import java.lang.reflect.Field;
import zombie.core.Core;
import zombie.input.GameKeyboard;
import zombie.ui.UITextBox2;

/**
 * Shows the IME's uncommitted composition (the syllable being typed) in the focused text box.
 *
 * The game only knows committed characters, so a search box did not filter by the syllable being
 * typed, and a syllable left uncommitted when the box lost focus (click elsewhere) was lost. The
 * composition is put into the box's internalText at the cursor, so it is drawn and onTextChange
 * handlers (recipe search reads getInternalText) see it. Around Core.updateKeyboard, where key and
 * character events are applied, it is taken out again, so the IME's real commit lands as normal
 * text and Enter submits only committed text plus that commit.
 *
 * If the box stopped text entry while showing a composition (click elsewhere, hidden), the shown
 * text stays as committed text and the IME's copy is cancelled. Esc is a key event, handled while
 * the composition is out, so Esc drops it, as Windows does.
 *
 * Main thread only. Single-line, unmasked boxes only.
 */
public final class Preedit {
    private Preedit() {}

    static UITextBox2 box;   // box showing a composition, or null
    static String text = ""; // the composition shown
    static int pos;          // where it starts in box.internalText
    static int kept;         // compositions kept as text when a box lost focus

    private static Field selection;
    private static boolean selectionFailed;

    /** Core.updateKeyboard enter: take the shown composition out before events are applied. */
    public static void before() {
        UITextBox2 b = box;
        if (b == null) return;
        String t = text;
        int p = pos;
        box = null;
        text = "";
        if (Core.currentTextEntryBox != b || !Ime.typing()) {
            // focus left the box: the shown syllable stays as typed text, the IME drops its copy
            kept++;
            Ime.cancelRequested = true;
            return;
        }
        String s = b.internalText;
        if (p < 0 || p + t.length() > s.length() || !s.startsWith(t, p)) return; // text was replaced
        int cursor = b.getCursorPos();
        b.internalText = s.substring(0, p) + s.substring(p + t.length());
        b.setCursorPos(cursor >= p + t.length() ? cursor - t.length() : Math.min(cursor, p));
    }

    /** Core.updateKeyboard exit: show the composition that goes with the events just applied. */
    public static void after() {
        String old = lastShown;
        UITextBox2 lastBox = lastBoxShown;
        lastShown = "";
        lastBoxShown = null;
        if (!Ime.typing() || !(Core.currentTextEntryBox instanceof UITextBox2)) return;
        UITextBox2 b = (UITextBox2) Core.currentTextEntryBox;
        String c = Ime.compositionFor(GameKeyboard.getEventQueue());
        if (!c.isEmpty() && usable(b, c)) {
            int cursor = b.getCursorPos();
            String s = b.internalText;
            b.internalText = s.substring(0, cursor) + c + s.substring(cursor);
            b.setCursorPos(cursor + c.length());
            box = b;
            text = c;
            pos = cursor;
            lastShown = c;
            lastBoxShown = b;
        }
        if (b == lastBox ? !c.equals(old) : !lastShown.isEmpty()) b.onTextChange();
    }

    private static String lastShown = "";
    private static UITextBox2 lastBoxShown;

    private static boolean usable(UITextBox2 b, String c) {
        if (b.isMasked() || b.isMultipleLine() || b.isOnlyNumbers() || b.isOnlyText()) return false;
        int cursor = b.getCursorPos();
        if (cursor < 0 || cursor > b.internalText.length()) return false;
        if (b.getMaxTextLength() > -1 && b.internalText.length() + c.length() > b.getMaxTextLength()) return false;
        return selectionAt(b) == cursor;
    }

    private static int selectionAt(UITextBox2 b) {
        if (selectionFailed) return -1;
        try {
            if (selection == null) {
                selection = UITextBox2.class.getDeclaredField("toSelectionIndex");
                selection.setAccessible(true);
            }
            return selection.getInt(b);
        } catch (Throwable t) {
            selectionFailed = true;
            Log.info("WARN no UITextBox2.toSelectionIndex (" + t + "); composition not shown in boxes");
            return -1;
        }
    }
}
