package talkinghand.imekeyfix;

/** Entry point called by ZombieBuddy. */
public class Main {
    public static void main(String[] args) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) {
            Ime.enabled = false;
            Log.info("not Windows, nothing to do");
            return;
        }
        Log.info("loaded");
    }
}
