package dev.blueprintplanner.compat.ldl;

import com.mojang.logging.LogUtils;
import dev.blueprintplanner.util.ChunkRenderHelper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

/**
 * LambDynamicLights keeps one global set of light sources and only resets it on a real world change. A planner
 * switch has to do the same, otherwise lights of one world shine on in the other. Reflection keeps LambDynamicLights
 * optional (its API types live in a nested jar).
 */
public final class LdlCompat {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static boolean initialized;
	private static boolean failed;
	private static Method get;
	private static Field lock;
	private static Method removeLightSources;
	private static Field engine;
	private static Method computeSpatialLookup;
	private static Class<?> beaconSource;
	private static Class<?> guardianSource;
	private static Method beaconSetBeam;
	private static Method guardianSetBeam;

	private LdlCompat() {
	}

	private static boolean init() {
		if (initialized || failed) {
			return initialized;
		}
		try {
			Class<?> ldl = Class.forName("dev.lambdaurora.lambdynlights.LambDynLights");
			get = ldl.getMethod("get");
			lock = ldl.getDeclaredField("lightSourcesLock");
			lock.setAccessible(true);
			removeLightSources = ldl.getMethod("removeLightSources", Predicate.class);
			engine = ldl.getField("engine");
			computeSpatialLookup = engine.getType().getMethod("computeSpatialLookup", Collection.class);
			beaconSource = Class.forName("dev.lambdaurora.lambdynlights.echo.BeaconBlockEntityLightSource");
			guardianSource = Class.forName("dev.lambdaurora.lambdynlights.echo.GuardianEntityLightSource");
			beaconSetBeam = findSetter(beaconSource);
			guardianSetBeam = findSetter(guardianSource);
			initialized = true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			failed = true;
			LOGGER.warn("Blueprint Planner: LambDynamicLights changed, dynamic lights may leak between planner and real world", e);
		}
		return initialized;
	}

	private static Method findSetter(Class<?> type) throws NoSuchMethodException {
		for (Method method : type.getMethods()) {
			if (method.getName().equals("lambdynlights$setDynamicLightBeam") && method.getParameterCount() == 1) {
				return method;
			}
		}
		throw new NoSuchMethodException(type.getName() + ".lambdynlights$setDynamicLightBeam");
	}

	/** Called right after a full world switch; the light sources still belong to the world that was left. */
	public static void onWorldSwitch(ClientLevel shown) {
		if (!init()) {
			return;
		}
		try {
			Object instance = get.invoke(null);
			ReentrantReadWriteLock sourcesLock = (ReentrantReadWriteLock) lock.get(instance);
			sourcesLock.writeLock().lock();
			try {
				Predicate<Object> all = source -> true;
				removeLightSources.invoke(instance, all);
				computeSpatialLookup.invoke(engine.get(instance), List.of());
			} finally {
				sourcesLock.writeLock().unlock();
			}
			// beams register themselves again on their next tick once their behaviour is dropped
			for (Entity entity : shown.entitiesForRendering()) {
				if (guardianSource.isInstance(entity)) {
					guardianSetBeam.invoke(entity, (Object) null);
				}
			}
			ChunkRenderHelper.forEachLoadedChunk(shown, chunk -> {
				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					if (beaconSource.isInstance(blockEntity)) {
						try {
							beaconSetBeam.invoke(blockEntity, (Object) null);
						} catch (ReflectiveOperationException e) {
							throw new IllegalStateException(e);
						}
					}
				}
			});
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("Blueprint Planner: could not reset LambDynamicLights after a world switch", e);
		}
	}
}
