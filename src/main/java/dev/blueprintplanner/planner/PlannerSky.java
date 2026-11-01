package dev.blueprintplanner.planner;

import dev.blueprintplanner.config.PlannerConfig;
import java.util.Locale;
import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

/** Sky looks for the planner world, so it is obvious at a glance that you are not in the real world. */
public enum PlannerSky {
	NORMAL(null, null, 0.0F),
	BLUEPRINT(0x1E4E8C, 0x3C70B0, 0.5F),
	NIGHT(0x04060E, 0x0A0E1C, 1.0F),
	STUDIO(0xDDE1E8, 0xE8EAEF, 0.0F);

	private static final Vector4fc TRANSPARENT = ARGB.vector4fFromARGB32(0x00FFFFFF);

	private final @Nullable Vector3fc sky;
	private final @Nullable Vector3fc fog;
	private final float stars;

	PlannerSky(@Nullable Integer sky, @Nullable Integer fog, float stars) {
		this.sky = sky == null ? null : ARGB.vector3fFromRGB24(sky);
		this.fog = fog == null ? null : ARGB.vector3fFromRGB24(fog);
		this.stars = stars;
	}

	public String translationKey() {
		return "blueprintplanner.sky." + this.name().toLowerCase(Locale.ROOT);
	}

	public static PlannerSky current() {
		try {
			return valueOf(PlannerConfig.get().plannerSky);
		} catch (IllegalArgumentException | NullPointerException e) {
			return BLUEPRINT;
		}
	}

	/** Added to the planner's client level; the layers read the setting live, so changes apply immediately. */
	public static void addLayers(EnvironmentAttributeSystem.Builder builder) {
		builder.addTimeBasedLayer(EnvironmentAttributes.SKY_COLOR, (base, tick) -> current().sky != null ? current().sky : base);
		builder.addTimeBasedLayer(EnvironmentAttributes.FOG_COLOR, (base, tick) -> current().fog != null ? current().fog : base);
		builder.addTimeBasedLayer(EnvironmentAttributes.CLOUD_COLOR, (base, tick) -> current() == NORMAL ? base : TRANSPARENT);
		builder.addTimeBasedLayer(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, (base, tick) -> current() == NORMAL ? base : TRANSPARENT);
		builder.addTimeBasedLayer(EnvironmentAttributes.STAR_BRIGHTNESS, (base, tick) -> current() == NORMAL ? base : current().stars);
	}
}
