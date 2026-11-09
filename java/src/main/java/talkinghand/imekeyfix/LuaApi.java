package talkinghand.imekeyfix;

import me.zed_0xff.zombie_buddy.Exposer;

/** Lua table IMEKeyFix, for the console and tests. */
@Exposer.LuaClass(name = "IMEKeyFix")
public class LuaApi {
    public static String status() {
        return Ime.status();
    }

    /** false: leave the IME attached all the time (vanilla), true: detach it outside text boxes. */
    public static String setEnabled(boolean on) {
        Ime.enabled = on;
        return Ime.status();
    }

    /** 1 attached, 0 detached, -1 not applied yet (last state set on the window thread). */
    public static double applied() {
        return Ime.applied;
    }

    /** ImmGetContext right after the last switch: true when the window had an input context. */
    public static boolean checked() {
        return Boolean.TRUE.equals(Ime.checked);
    }

    public static double switches() {
        return Ime.switches;
    }

    public static boolean typing() {
        return Ime.typing();
    }

    public static String error() {
        return Ime.error;
    }

    /** Tests: next frame, fake Enter pressed during composition, then the IME committing text. */
    public static void fakeEnterDuringComposition(String text) {
        Ime.fakeInput(257, text, "");
    }

    /** Tests: next frame, one poll with a key (GLFW code, 0 none), the IME's commit, then this composition. */
    public static void fakeInput(double key, String commit, String compAfter) {
        Ime.fakeInput((int) key, commit == null ? "" : commit, compAfter == null ? "" : compAfter);
    }

    /** Tests: replace the IME's composition text from the next poll on (nil: the real IME again). */
    public static void setTestComposition(String text) {
        Ime.testComp = text;
    }

    /** Tests: back to the real IME's composition. */
    public static void clearTestComposition() {
        Ime.testComp = null;
    }

    /** How many times Enter was held back behind a committed IME character. */
    public static double deferredEnter() {
        return Ime.deferred;
    }

    /** How many IME compositions were cancelled (kept as text, or left in a box that lost focus). */
    public static double cancels() {
        return Ime.cancels;
    }

    /** Compositions kept as text when their box lost focus. */
    public static double kept() {
        return Preedit.kept;
    }
}
