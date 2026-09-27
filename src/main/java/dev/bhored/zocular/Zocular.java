package dev.bhored.zocular;

import dev.bhored.zocular.camera.CameraControl;
import dev.bhored.zocular.config.ZocularConfig;
import dev.bhored.zocular.gui.SettingsScreen;
import dev.bhored.zocular.hud.Overlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Zocular implements ClientModInitializer {
	public static final String MOD_ID = "zocular";
	public static final Logger LOGGER = LoggerFactory.getLogger("Zocular");

	public static final String DISCORD_URL = "https://discord.gg/bananasandwich";
	public static final String ISSUES_URL = "https://github.com/bossgaming8/zocular/issues";
	public static final String DONATE_URL = "https://www.paypal.com/paypalme/BhoredM";

	private static boolean openSettings;

	@Override
	public void onInitializeClient() {
		Keybinds.register();
		ZocularConfig.load();
		Overlay.register();

		ClientTickEvents.END_CLIENT_TICK.register(Zocular::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> CameraControl.get().onDisconnect(client));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ZocularConfig.save());
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
			ClientCommands.literal(MOD_ID).executes(command -> {
				// Opened on the next tick; the chat screen is still closing while the command runs.
				openSettings = true;
				return 1;
			})
		));
	}

	private static void tick(Minecraft minecraft) {
		if (openSettings) {
			openSettings = false;
			minecraft.gui.setScreen(new SettingsScreen(minecraft.gui.screen()));
		}
		while (Keybinds.SETTINGS.consumeClick()) {
			minecraft.gui.setScreen(new SettingsScreen(null));
		}
		CameraControl.get().tick(minecraft);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static String version() {
		return FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.map(version -> version.contains("+") ? version.substring(0, version.indexOf('+')) : version)
			.orElse("dev");
	}
}
