package dev.bhored.zocular.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.datafixers.util.Pair;
import dev.bhored.zocular.Keybinds;
import dev.bhored.zocular.camera.CameraControl;
import dev.bhored.zocular.camera.KeyframePath;
import dev.bhored.zocular.camera.Pose;
import dev.bhored.zocular.config.ZocularConfig;
import dev.bhored.zocular.gui.SettingsScreen;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Records the screenshots and the camera path clip used on the Modrinth page. Only runs when the game test run is
 * started with {@code -Pshowcase}; the frames are stitched together by tools/make_showcase.py.
 */
public final class ShowcaseRecorder implements FabricClientGameTest {
	private static final int RECORD_SECONDS = 40;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Boolean.getBoolean("zocular.showcase")) {
			return;
		}
		TestInput input = context.getInput();
		input.resizeWindow(1920, 1080);
		context.runOnClient(minecraft -> {
			minecraft.options.renderDistance().set(12);
			minecraft.options.bobView().set(false);
		});

		try (TestSingleplayerContext world = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(settings -> {
					settings.setSeed("zocular");
					settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
					settings.setDifficulty(Difficulty.PEACEFUL);
				})
				.create()) {
			Scene scene = world.getServer().computeOnServer(server -> findScene(server.overworld()));
			BlockPos spot = scene.position();
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamerule advance_weather false");
			world.getServer().runCommand("time set 11200");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("tp @p %d %d %d %.1f 22".formatted(spot.getX(), spot.getY(), spot.getZ(), scene.yaw()));
			// The test API waits for a full square of chunks, but the server only sends a circle, so those waits
			// never finish in a real world. Give the terrain a fixed amount of time to load instead.
			context.waitTicks(600);

			shot(context, "zoom-1x");
			input.holdKey(Keybinds.ZOOM);
			context.waitTicks(5);
			input.scroll(4.0);
			context.waitTicks(20);
			shot(context, "zoom-8x");
			input.releaseKey(Keybinds.ZOOM);
			context.waitTicks(20);

			input.pressKey(Keybinds.CINEMATIC);
			context.waitTicks(60);
			shot(context, "cinematic-1");
			for (int i = 2; i <= 4; i++) {
				input.pressKey(Keybinds.NEXT_SHOT);
				context.waitTicks(20);
				shot(context, "cinematic-" + i);
			}
			input.pressKey(Keybinds.CINEMATIC);
			context.waitTicks(50);

			input.pressKey(Keybinds.FREECAM);
			input.holdKeyFor(options -> options.keyUp, 24);
			input.holdKeyFor(options -> options.keyJump, 6);
			input.moveCursor(1200.0, 90.0);
			context.waitTicks(10);
			shot(context, "freecam");

			clip(context, context.computeOnClient(minecraft -> minecraft.player.position()));
			input.pressKey(Keybinds.FREECAM);
			context.waitTicks(5);

			context.setScreen(() -> new SettingsScreen(null));
			context.waitTicks(10);
			shot(context, "settings");
			input.pressKey(InputConstants.KEY_ESCAPE);
		}
	}

	/**
	 * Every screenshot lets the game run a few extra ticks while it waits for the image, so frames can't be spaced
	 * evenly by waiting. Instead the path plays slowly and linearly, each frame is named after the exact point in
	 * time it shows, and tools/make_showcase.py resamples them into an evenly timed, eased clip.
	 */
	private static void clip(ClientGameTestContext context, Vec3 player) {
		context.runOnClient(minecraft -> {
			ZocularConfig.CameraPath config = ZocularConfig.get().path;
			config.duration = RECORD_SECONDS;
			config.ease = false;
			config.constantSpeed = true;
			config.letterbox = false;
			config.hideHud = true;

			KeyframePath path = CameraControl.get().path();
			path.clear();
			Vec3 focus = player.add(0.0, 1.5, 0.0);
			float[][] arc = {{100, 7.5F, 6.5F}, {145, 10.0F, 4.5F}, {195, 13.0F, 3.5F}, {245, 10.5F, 5.0F}, {290, 7.0F, 8.0F}};
			for (float[] point : arc) {
				float angle = point[0] * Mth.DEG_TO_RAD;
				Vec3 position = focus.add(-Mth.sin(angle) * point[1], point[2], Mth.cos(angle) * point[1]);
				path.add(new Pose(position, Pose.yawTowards(position, focus), Pose.pitchTowards(position, focus), 0.0F));
			}
			CameraControl.get().togglePathPlayback(minecraft);
		});

		Field ticks = playbackTicks();
		int frame = 0;
		while (true) {
			int tick = context.computeOnClient(minecraft -> {
				KeyframePath path = CameraControl.get().path();
				return path.isPlaying() ? readTicks(ticks, path) : -1;
			});
			if (tick < 0 || tick >= RECORD_SECONDS * 20) {
				break;
			}
			context.takeScreenshot(TestScreenshotOptions.of("frame-%04d-t%05d".formatted(frame++, tick))
				.disableCounterPrefix()
				.withDeltaTicks(0.0F)
				.withSize(1280, 720)
				.withDestinationDir(Path.of("screenshots", "clip")));
		}
		context.waitTicks(20);
	}

	private static Field playbackTicks() {
		try {
			Field field = KeyframePath.class.getDeclaredField("ticks");
			field.setAccessible(true);
			return field;
		} catch (NoSuchFieldException e) {
			throw new IllegalStateException(e);
		}
	}

	private static int readTicks(Field field, KeyframePath path) {
		try {
			return field.getInt(path);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of("showcase-" + name).disableCounterPrefix());
	}

	private static Path clipDir() {
		return Path.of("screenshots", "clip");
	}

	/** A hilltop in a meadow, plus the direction with the best view downhill. */
	private static Scene findScene(ServerLevel level) {
		BlockPos origin = level.getRespawnData().pos();
		BlockPos center = origin;
		for (ResourceKey<Biome> biome : List.of(Biomes.MEADOW, Biomes.CHERRY_GROVE, Biomes.PLAINS)) {
			Pair<BlockPos, Holder<Biome>> result = level.findClosestBiome3d(holder -> holder.is(biome), origin, 4000, 32, 64);
			if (result != null) {
				center = result.getFirst();
				break;
			}
		}

		BlockPos best = null;
		for (int dx = -48; dx <= 48; dx += 4) {
			for (int dz = -48; dz <= 48; dz += 4) {
				BlockPos top = surface(level, center.getX() + dx, center.getZ() + dz);
				boolean ground = level.getBlockState(top.below()).is(BlockTags.DIRT);
				if (ground && (best == null || top.getY() > best.getY())) {
					best = top;
				}
			}
		}
		if (best == null) {
			best = surface(level, center.getX(), center.getZ());
		}

		float bestYaw = 0.0F;
		int lowest = Integer.MAX_VALUE;
		for (int i = 0; i < 16; i++) {
			float yaw = i * 22.5F;
			float rad = yaw * Mth.DEG_TO_RAD;
			int height = 0;
			for (int step = 12; step <= 48; step += 12) {
				height += surface(level, best.getX() + Math.round(-Mth.sin(rad) * step), best.getZ() + Math.round(Mth.cos(rad) * step)).getY();
			}
			if (height < lowest) {
				lowest = height;
				bestYaw = yaw;
			}
		}
		return new Scene(best, bestYaw);
	}

	private static BlockPos surface(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	private record Scene(BlockPos position, float yaw) {
	}
}
