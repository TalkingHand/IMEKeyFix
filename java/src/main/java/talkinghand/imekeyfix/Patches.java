package talkinghand.imekeyfix;

import me.zed_0xff.zombie_buddy.Patch;

public class Patches {
    /** Runs on the window thread every frame, before the window messages are read. */
    @Patch(className = "org.lwjglx.opengl.Display", methodName = "processMessages")
    public static class ProcessMessages {
        @Patch.OnEnter
        public static void enter() {
            Ime.update();
        }
    }
}
