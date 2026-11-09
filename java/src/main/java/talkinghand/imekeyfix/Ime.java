package talkinghand.imekeyfix;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjglx.opengl.Display;
import zombie.core.Core;
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
 * IMM calls only work on the thread that owns the window, so {@link #update()} runs from
 * Display.processMessages (just before glfwPollEvents).
 */
public final class Ime {
    private Ime() {}

    private static final int IACE_DEFAULT = 0x0010;
    private static final int GCS_COMPSTR = 0x0008;
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

    // Enter while composing: see deferKey()
    static boolean composing;      // the IME had an uncommitted composition when this frame's poll began
    static int deferred;           // Enter events moved behind the commit, for status()
    static String testChar;        // tests: fake one "Enter during composition" next frame
    private static final java.util.ArrayList<int[]> held = new java.util.ArrayList<>();
    private static boolean flushing;

    /** Whether the player is typing in an editable, visible text box (search, chat, sign text). */
    static boolean typing() {
        UITextEntryInterface box = Core.currentTextEntryBox;
        if (box == null || !box.isEditable() || !box.isDoingTextEntry()) return false;
        return !(box instanceof UIElement) || ((UIElement) box).isReallyVisible();
    }

    public static void update() {
        if (broken) return;
        try {
            composing = applied == 1 && compositionLength() > 0;
            long window = Display.getWindow();
            if (window == 0L) return;
            long h = GLFWNativeWin32.glfwGetWin32Window(window);
            if (h == 0L) return;
            int want = (!enabled || typing()) ? 1 : 0;
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

    /** Whether the window has an input context right now (window thread only, else null). */
    static Boolean attached() {
        if (associate == null || hwnd == 0L) return null;
        Pointer ctx = (Pointer) getContext.invoke(Pointer.class, new Object[]{new Pointer(hwnd)});
        if (ctx == null) return false;
        releaseContext.invokeInt(new Object[]{new Pointer(hwnd), ctx});
        return true;
    }

    /** Length in bytes of the window's uncommitted composition, 0 if none (window thread only). */
    static int compositionLength() {
        if (compositionString == null || hwnd == 0L) return 0;
        Pointer ctx = (Pointer) getContext.invoke(Pointer.class, new Object[]{new Pointer(hwnd)});
        if (ctx == null) return 0;
        try {
            return compositionString.invokeInt(new Object[]{ctx, GCS_COMPSTR, Pointer.NULL, 0});
        } finally {
            releaseContext.invokeInt(new Object[]{new Pointer(hwnd), ctx});
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

    /** Display.processMessages exit: queue the held key events behind this poll's characters. */
    public static void flush() {
        if (held.isEmpty()) {
            composing = false;
            return;
        }
        flushing = true;
        try {
            for (int[] e : held) org.lwjglx.input.Keyboard.addKeyEvent(e[0], e[1]);
            deferred++;
        } finally {
            held.clear();
            flushing = false;
            composing = false;
        }
    }

    /** Tests: next frame, act as if Enter was pressed during composition and the IME then committed c. */
    public static void fakeEnterDuringComposition() {
        String c = testChar;
        if (c == null) return;
        testChar = null;
        composing = true;
        org.lwjglx.input.Keyboard.addKeyEvent(GLFW_KEY_ENTER, 1);
        org.lwjglx.input.Keyboard.addKeyEvent(GLFW_KEY_ENTER, 0);
        for (char ch : c.toCharArray()) org.lwjglx.input.Keyboard.addCharEvent(ch);
    }

    private static void load() {
        NativeLibrary imm = NativeLibrary.getInstance("imm32");
        associate = imm.getFunction("ImmAssociateContextEx");
        getContext = imm.getFunction("ImmGetContext");
        releaseContext = imm.getFunction("ImmReleaseContext");
        compositionString = imm.getFunction("ImmGetCompositionStringW");
    }

    private static void fail(String msg) {
        // never retry every frame: log once and leave the window as it is
        broken = true;
        error = msg;
        Log.info("ERROR " + msg + "; IME switching disabled for this session");
    }

    static String status() {
        return "enabled=" + enabled + " typing=" + typing() + " applied=" + applied
                + " checked=" + checked + " switches=" + switches + " deferredEnter=" + deferred + " thread=" + thread + (error != null ? " error=" + error : "");
    }
}
