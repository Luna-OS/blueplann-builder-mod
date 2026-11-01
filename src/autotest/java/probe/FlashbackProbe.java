package probe;

import java.lang.reflect.Field;
import net.fabricmc.loader.api.FabricLoader;

/** Drives a Flashback recording across planner switches (recording + planner used to crash the game). */
public final class FlashbackProbe {
	private FlashbackProbe() {
	}

	public static boolean loaded() {
		return FabricLoader.getInstance().isModLoaded("flashback");
	}

	public static void start() {
		call("startRecordingReplay");
	}

	public static void cancel() {
		if (recorder() != null) {
			call("cancelRecordingReplay");
		}
	}

	/** null = not recording, otherwise whether the recording is paused. */
	public static Boolean paused() {
		Object recorder = recorder();
		if (recorder == null) {
			return null;
		}
		try {
			return (Boolean) recorder.getClass().getMethod("isPaused").invoke(recorder);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Object recorder() {
		try {
			Field field = Class.forName("com.moulberry.flashback.Flashback").getField("RECORDER");
			return field.get(null);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void call(String method) {
		try {
			Class.forName("com.moulberry.flashback.Flashback").getMethod(method).invoke(null);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
