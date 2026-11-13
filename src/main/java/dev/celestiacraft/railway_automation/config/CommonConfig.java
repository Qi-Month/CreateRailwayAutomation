package dev.celestiacraft.railway_automation.config;

import dev.celestiacraft.railway_automation.RailwayAutomation;
import net.minecraftforge.common.ForgeConfigSpec;

public class CommonConfig {
	public static final int DEFAULT_MAX_TURN_SPAN = 32;
	public static final int DEFAULT_MIN_TURN_SPAN = 8;
	public static final int DEFAULT_MAX_VERTICAL_RISE = 32;
	public static final int DEFAULT_SLOPE_CURVE_LENGTH = 16;
	public static final double DEFAULT_GENTLE_SLOPE_RATIO = 0.225D;
	public static final int DEFAULT_SLOPE_INSERT_RISE = 16;

	public static final ForgeConfigSpec SPEC;
	public static final ForgeConfigSpec.IntValue MAX_TURN_SPAN;
	public static final ForgeConfigSpec.IntValue MIN_TURN_SPAN;
	public static final ForgeConfigSpec.IntValue MAX_VERTICAL_RISE;
	public static final ForgeConfigSpec.IntValue SLOPE_CURVE_LENGTH;
	public static final ForgeConfigSpec.DoubleValue GENTLE_SLOPE_RATIO;
	public static final ForgeConfigSpec.IntValue SLOPE_INSERT_RISE;

	private static final String SECTION_TRACK_PLACER = "track_placer";

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

		builder.comment("Turn placement settings of the track placer.")
				.comment("All settings below will only take effect after restarting the server or client.")
				.translation(translationKey(SECTION_TRACK_PLACER))
				.push(SECTION_TRACK_PLACER);

		MAX_TURN_SPAN = builder
				.translation(key("max_turn_span"))
				.comment("Largest turn span (blocks) a single turn placement may use.")
				.comment("The curve radius is roughly the span minus 0.5, a larger span gives a gentler curve.")
				.comment("type: int")
				.comment("default: 32")
				.defineInRange("maxTurnSpan", DEFAULT_MAX_TURN_SPAN, 8, 64);

		MIN_TURN_SPAN = builder
				.translation(key("min_turn_span"))
				.comment("Smallest turn span (blocks) that still forms a valid curve.")
				.comment("A span below this value makes the position unusable: nothing is placed and nothing is cleared.")
				.comment("Create itself requires a 90 degree turn to span at least 8 blocks.")
				.comment("type: int")
				.comment("default: 8")
				.defineInRange("minTurnSpan", DEFAULT_MIN_TURN_SPAN, 2, 512);

		MAX_VERTICAL_RISE = builder
				.translation(key("max_vertical_rise"))
				.comment("Largest height difference (blocks) allowed between two parallel tracks.")
				.comment("A target above this difference makes the position unusable: nothing is placed and nothing is cleared.")
				.comment("type: int")
				.comment("default: 32")
				.defineInRange("maxVerticalRise", DEFAULT_MAX_VERTICAL_RISE, 8, 64);

		SLOPE_CURVE_LENGTH = builder
				.translation(key("slope_curve_length"))
				.comment("Horizontal length (blocks) of each of the two curves that join a 45 degree slope.")
				.comment("The vertical rise of such a curve is always half of this value, which keeps a 1:2 ratio.")
				.comment("A larger value gives a gentler transition and needs more horizontal space.")
				.comment("type: int")
				.comment("default: 16")
				.defineInRange("slopeCurveLength", DEFAULT_SLOPE_CURVE_LENGTH, 2, 32);

		GENTLE_SLOPE_RATIO = builder
				.translation(key("gentle_slope_ratio"))
				.comment("Largest slope ratio (height difference divided by horizontal distance) of a gentle climb.")
				.comment("When the configured target is long enough to keep the slope below this ratio, the whole climb becomes")
				.comment("a single gentle curve instead of a 45 degree slope with transition curves, which keeps the train fast.")
				.comment("0.225 equals about 12.7 degrees and matches the curve exemption of Create navigation.")
				.comment("type: double")
				.comment("default: 0.225")
				.defineInRange("gentleSlopeRatio", DEFAULT_GENTLE_SLOPE_RATIO, 0.05D, 1.0D);

		SLOPE_INSERT_RISE = builder
				.translation(key("slope_insert_rise"))
				.comment("Height difference (blocks) above which a 45 degree slope is inserted into a climb.")
				.comment("A target above this difference always uses the 45 degree slope with transition curves.")
				.comment("At or below this difference a gentle curve is preferred; the nearer the difference is to zero the more")
				.comment("horizontal distance the placer is willing to spend on the curve instead of the slope.")
				.comment("type: int")
				.comment("default: 16")
				.defineInRange("slopeInsertRise", DEFAULT_SLOPE_INSERT_RISE, 4, 64);

		builder.pop();

		SPEC = builder.build();
	}

	private CommonConfig() {
	}

	public static String translationKey(String... path) {
		return RailwayAutomation.MODID + ".config." + String.join(".", path);
	}

	private static String key(String name) {
		return translationKey(SECTION_TRACK_PLACER, name);
	}

	public static int maxTurnSpan() {
		try {
			return MAX_TURN_SPAN.get();
		} catch (IllegalStateException e) {
			return DEFAULT_MAX_TURN_SPAN;
		}
	}

	public static int minTurnSpan() {
		try {
			return MIN_TURN_SPAN.get();
		} catch (IllegalStateException e) {
			return DEFAULT_MIN_TURN_SPAN;
		}
	}

	public static int maxVerticalRise() {
		try {
			return MAX_VERTICAL_RISE.get();
		} catch (IllegalStateException e) {
			return DEFAULT_MAX_VERTICAL_RISE;
		}
	}

	public static int slopeCurveLength() {
		int configured;
		try {
			configured = SLOPE_CURVE_LENGTH.get();
		} catch (IllegalStateException e) {
			configured = DEFAULT_SLOPE_CURVE_LENGTH;
		}
		return Math.max(2, configured - configured % 2);
	}

	public static int slopeCurveRise() {
		return Math.max(1, slopeCurveLength() / 2);
	}

	public static int slopeInsertRise() {
		try {
			return SLOPE_INSERT_RISE.get();
		} catch (IllegalStateException e) {
			return DEFAULT_SLOPE_INSERT_RISE;
		}
	}

	public static double gentleSlopeRatio() {
		double configured;
		try {
			configured = GENTLE_SLOPE_RATIO.get();
		} catch (IllegalStateException e) {
			configured = DEFAULT_GENTLE_SLOPE_RATIO;
		}
		return Math.max(0.05D, configured);
	}
}
