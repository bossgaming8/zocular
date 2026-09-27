package dev.bhored.zocular.util;

/**
 * Spreads mouse movement over the next few frames. No input is dropped; it just arrives gradually.
 */
public final class LookSmoothing {
	private double pendingX;
	private double pendingY;

	public double[] apply(double dx, double dy, double frameTime, float strength) {
		pendingX += dx;
		pendingY += dy;
		double portion = strength <= 0.0F ? 1.0 : Motion.follow(strength * 0.25, frameTime);
		double x = pendingX * portion;
		double y = pendingY * portion;
		pendingX -= x;
		pendingY -= y;
		return new double[] {x, y};
	}
}
