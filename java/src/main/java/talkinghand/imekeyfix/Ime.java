package talkinghand.imekeyfix;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import java.util.IdentityHashMap;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjglx.input.Keyboard;
import org.lwjglx.opengl.Display;
import zombie.core.Core;
import zombie.input.GameKeyboard;
import zombie.ui.UIElement;
import zombie.ui.UITextEntryInterface;

/**
 * Detaches the Windows IME from the game window while no text box is being typed in.
 *
 * With a Korean/Chinese/Japanese IME in native mode, Windows hands key presses to the IME and
 * GLFW only sees VK_PROCESSKEY, which it drops, so game hotkeys do nothing. Without an input
 * context on the window the keys reach GLFW as plain keys. The thread's input context itself is
 * kept, so attaching it again restores the IME with its last mode (e.g. Hangul) for text boxes.
 *
 * IMM calls only work on the thread that owns the window, so everything here runs from
 * Display.processMessages (around glfwPollEvents). The text box side is in {@link Preedit}.
 */
public final class Ime {
    private Ime() {}

    private static final int IACE_DEFAULT = 0x0010;
    private static final int GCS_COMPSTR = 0x0008;
    private static final int NI_COMPOSITIONSTR = 0x0015;
    private static final int CPS_CANCEL = 0x0004;
    private static final int GLFW_KEY_ENTER = 257;
    private static final int GLFW_KEY_KP_ENTER = 335;

    /** User switch: false keeps the IME attached all the time (vanilla behaviour). */
    public static volatile boolean enabled = true;

    // state, written on the window thread only
    static long hwnd;
    static int applied = -1;       // -1 unknown, 0 detached, 1 attached
    static int switches;
    static Boolean checked;        // ImmGetContext right after the last switch
    static String error;
    static String thread;
    private static boolean broken;
    private static Function associate;
    private static Function getContext;
    private static Function releaseContext;
    private static Function compositionString;
    private static Function notify;

    // Enter while composing: see deferKey()
    static boolean composing;      // the IME had an uncommitted composition when this frame's poll began
    static int deferred;           // Enter events moved behind the commit, for status()
    private static final java.util.ArrayList<int[]> held = new java.util.ArrayList<>();
    private static boolean flushing;

    // composition text after each poll, per keyboard event queue (see recordComposition())
    private static final IdentityHashMap<Object, String> comps = new IdentityHashMap<>();
    /** Set by the main thread when a composition was kept as text: drop the IME's copy. */
    static volatile boolean cancelRequested;
    static int cancels;

    // tests: fake IME input. testComp replaces the IME's composition text while non-null.
    static volatile String testComp;
    private static volatile Object[] testInput;   // {key, commit, compAfter}

    /** Whether the player is typing in an editable, visible text box (search, chat, sign text). */
    static boolean typing() {
        UITextEntryInterface box = Core.currentTextEntryBox;
        if (box == null || !box.isEditable() || !box.isDoingTextEntry()) return false;
        return !(box instanceof UIElement) || ((UIElement) box).isReallyVisible();
    }

    /** Display.processMessages enter, before glfwPollEvents. */
    public static void update() {
        if (broken) return;
        try {
            composing = applied == 1 && !composition().isEmpty();
            long window = Display.getWindow();
            if (window == 0L) return;
            long h = GLFWNativeWin32.glfwGetWin32Window(window);
            if (h == 0L) return;
            int want = (!enabled || typing()) ? 1 : 0;
            if (applied == 1 && h == hwnd && (cancelRequested || (want == 0 && composing))) {
                // a composition kept as text by Preedit, or left behind in a box that lost focus
                cancelComposition();
                composing = false;
            }
            cancelRequested = false;
            if (h == hwnd && want == applied) return;
            if (associate == null) load();
            if (thread == null) {
                thread = Thread.currentThread().getName();
                Log.info("IME control on thread " + thread);
            }
            int ok = associate.invokeInt(new Object[]{new Pointer(h), Pointer.NULL, want == 1 ? IACE_DEFAULT : 0});
            if (ok == 0) {
                fail("ImmAssociateContextEx returned 0 (want=" + want + ")");
                return;
            }
            if (h != hwnd) Log.info("window " + Long.toHexString(h) + (want == 1 ? ": IME attached" : ": IME detached"));
            hwnd = h;
            applied = want;
            switches++;
            checked = attached();
            if (checked == null || checked != (want == 1)) Log.info("WARN input context check: " + checked + ", want " + want);
        } catch (Throwable t) {
            fail(t.toString());
        }
    }

    /** Display.processMessages exit: held keys go behind this poll's characters, then the composition is noted. */
    public static void afterPoll() {
        flush();
        recordComposition();
    }

    /** Whether the window has an input context right now (window thread only, else null). */
    static Boolean attached() {
        if (associate == null || hwnd == 0L) return null;
        Pointer ctx = (Pointer) getContext.invoke(Pointer.class, new Object[]{new Pointer(hwnd)});
        if (ctx == null) return false;
        releaseContext.invokeInt(new Object[]{new Pointer(hwnd), ctx});
        return true;
    }

    /** The window's uncommitted composition text, "" if none (window thread only). */
    static String composition() {
        String t = testComp;
        if (t != null) return t;
        if (compositionString == null || hwnd == 0L || applied != 1) return "";
        Pointer ctx = (Pointer) getContext.invoke(Pointer.class, new Object[]{new Pointer(hwnd)});
        if (ctx == null) return "";
        try {
            int bytes = compositionString.invokeInt(new Object[]{ctx, GCS_COMPSTR, Pointer.NULL, 0});
            if (bytes <= 0) return "";
            Memory buf = new Memory(bytes);
            int got = compositionString.invokeInt(new Object[]{ctx, GCS_COMPSTR, buf, bytes});
            if (got <= 0) return "";
            return new String(buf.getCharArray(0, got / 2));
        } finally {
            releaseContext.invokeInt(new Object[]{new Pointer(hwnd), ctx});
        }
    }

    /** Drop the IME's uncommitted composition (window thread only). */
    private static void cancelComposition() {
        cancels++;
        if (testComp != null) {
            testComp = "";
            return;
        }
        if (notify == null || hwnd == 0L) return;
        Pointer ctx = (Pointer) getContext.invoke(Pointer.class, new Object[]{new Pointer(hwnd)});
        if (ctx == null) return;
        try {
            notify.invokeInt(new Object[]{ctx, NI_COMPOSITIONSTR, CPS_CANCEL, 0});
        } finally {
            releaseContext.invokeInt(new Object[]{new Pointer(hwnd), ctx});
        }
    }

    /**
     * The composition after this poll, stored with the event queue that got this poll's key and
     * character events. The main thread reads the queue's events and its composition together
     * ({@link #compositionFor}), so a commit (character event + composition gone) is never seen half.
     */
    private static void recordComposition() {
        String c = "";
        try {
            if (!broken && applied == 1) c = composition();
        } catch (Throwable t) {
            c = "";
        }
        Object q = GameKeyboard.getEventQueuePolling();
        synchronized (comps) {
            comps.put(q, c);
        }
    }

    /** Main thread: the composition that goes with the event queue being read this frame. */
    static String compositionFor(Object queue) {
        synchronized (comps) {
            String s = comps.get(queue);
            return s == null ? "" : s;
        }
    }

    /**
     * Keyboard.addKeyEvent hook (GLFW key callback, inside glfwPollEvents). Pressing Enter while a
     * syllable is still being composed makes the IME commit it, but its character (WM_CHAR) comes
     * after the Enter key in the same poll. The game handles Enter first (submit, close the box)
     * and drops the character, so the last unfinished syllable (e.g. a lone ㅋ) is lost. Enter is
     * held back here and queued after the poll, behind the committed character.
     *
     * @return true when the event was held (skip the original)
     */
    public static boolean deferKey(int key, int status) {
        if (flushing) return false;
        if (!held.isEmpty() || (composing && (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER))) {
            held.add(new int[]{key, status});
            return true;
        }
        return false;
    }

    private static void flush() {
        if (held.isEmpty()) {
            composing = false;
            return;
        }
        flushing = true;
        try {
            for (int[] e : held) Keyboard.addKeyEvent(e[0], e[1]);
            deferred++;
        } finally {
            held.clear();
            flushing = false;
            composing = false;
        }
    }

    /** Tests: next frame, a key (GLFW code, 0 = none) and the IME's commit arrive in one poll, key first. */
    static void fakeInput(int key, String commit, String compAfter) {
        testInput = new Object[]{key, commit, compAfter};
    }

    /** Display.processMessages enter, after update(): replay a fake input as one poll would. */
    public static void runFakeInput() {
        Object[] in = testInput;
        if (in == null) return;
        testInput = null;
        int key = (Integer) in[0];
        if (key == GLFW_KEY_ENTER) composing = true;
        if (key != 0) {
            Keyboard.addKeyEvent(key, 1);
            Keyboard.addKeyEvent(key, 0);
        }
        for (char ch : ((String) in[1]).toCharArray()) Keyboard.addCharEvent(ch);
        testComp = (String) in[2];
    }

    private static void load() {
        NativeLibrary imm = NativeLibrary.getInstance("imm32");
        associate = imm.getFunction("ImmAssociateContextEx");
        getContext = imm.getFunction("ImmGetContext");
        releaseContext = imm.getFunction("ImmReleaseContext");
        compositionString = imm.getFunction("ImmGetCompositionStringW");
        notify = imm.getFunction("ImmNotifyIME");
    }

    private static void fail(String msg) {
        // never retry every frame: log once and leave the window as it is
        broken = true;
        error = msg;
        Log.info("ERROR " + msg + "; IME switching disabled for this session");
    }

    static String status() {
        return "enabled=" + enabled + " typing=" + typing() + " applied=" + applied
                + " checked=" + checked + " switches=" + switches + " deferredEnter=" + deferred
                + " cancels=" + cancels + " preedit=[" + Preedit.text + "] kept=" + Preedit.kept
                + " thread=" + thread + (error != null ? " error=" + error : "");
    }
}
