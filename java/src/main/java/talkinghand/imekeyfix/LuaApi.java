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
}
