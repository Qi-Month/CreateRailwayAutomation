package dev.celestiacraft.railway_automation.compat.railways;

public class RailwaysCompat {
	public static final String MOD_ID = "railways";
	public static final double WIDE_GAUGE_SHIFT = 0.5D;
	private static final String MONORAIL_GAUGE = "monorail";
	private static final String WIDE_GAUGE = "wide_gauge";

	public static boolean isMonorail(String gauge) {
		return MONORAIL_GAUGE.equals(gauge);
	}

	public static boolean isWideGauge(String gauge) {
		return WIDE_GAUGE.equals(gauge);
	}
}
