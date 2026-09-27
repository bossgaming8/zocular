package dev.bhored.zocular.compat;

import dev.bhored.zocular.Zocular;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Talks to Iris through its public v0 API by reflection, so Zocular never needs Iris on the classpath.
 */
public final class IrisShaders {
	private static boolean resolved;
	private static Object config;
	private static Method areEnabled;
	private static Method setEnabled;

	private IrisShaders() {
	}

	public static boolean isInstalled() {
		return FabricLoader.getInstance().isModLoaded("iris");
	}

	public static Boolean enabled() {
		if (!resolve()) {
			return null;
		}
		try {
			return (Boolean) areEnabled.invoke(config);
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	public static void setEnabled(boolean enabled) {
		if (!resolve()) {
			return;
		}
		try {
			setEnabled.invoke(config, enabled);
		} catch (ReflectiveOperationException e) {
			Zocular.LOGGER.warn("Could not toggle Iris shaders", e);
		}
	}

	private static boolean resolve() {
		if (!resolved) {
			resolved = true;
			if (!isInstalled()) {
				return false;
			}
			try {
				Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				Object instance = api.getMethod("getInstance").invoke(null);
				config = api.getMethod("getConfig").invoke(instance);
				Class<?> configType = Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig");
				areEnabled = configType.getMethod("areShadersEnabled");
				setEnabled = configType.getMethod("setShadersEnabledAndApply", boolean.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				Zocular.LOGGER.warn("Iris is installed but its API could not be reached", e);
				config = null;
			}
		}
		return config != null;
	}
}
