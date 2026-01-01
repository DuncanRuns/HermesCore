package me.duncanruns.hermes.core;

import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

public class HermesCore implements ModInitializer {
    public static final Path GAME_DIR = FabricLoader.getInstance().getGameDir().normalize().toAbsolutePath();
    public static final Path LOCAL_HERMES_FOLDER = GAME_DIR.resolve("hermes");
    public static final Path GLOBAL_HERMES_FOLDER = getGlobalPath();
    public static final boolean IS_CLIENT = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;

    /**
     * @author me-nx, DuncanRuns
     */
    private static Path getGlobalPath() {
        // Copy of mojang logic to not depend on it, helps with porting
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            for (Supplier<String> possibleEnv : Arrays.<Supplier<String>>asList(
                    () -> System.getenv("LOCALAPPDATA"),
                    () -> System.getenv("APPDATA"),
                    () -> System.getProperty("user.home")
            )) {
                String base = possibleEnv.get();
                if (base == null) continue;
                return Paths.get(base, "MCSRHermes");
            }
            throw new RuntimeException("Failed to find a suitable path for Hermes");
        } else if (osName.contains("mac")) {
            return Paths.get(System.getProperty("user.home"), "Library", "Application Support", "MCSRHermes");
        } else if (osName.contains("linux") || osName.contains("unix")) {
            return Optional.ofNullable(System.getenv("XDG_RUNTIME_DIR"))
                    .map((runtimeDir) -> Paths.get(runtimeDir, "MCSRHermes"))
                    .orElse(Paths.get(System.getProperty("user.home"), ".local", "share", "MCSRHermes"));
        } else {
            return Paths.get(System.getProperty("user.home"), "MCSRHermes");
        }
    }

    @Override
    public void onInitialize() {
        if (FabricLoader.getInstance().isModLoaded("hermes")) return;
        InstanceInfo.init();
    }

    public static JsonObject pathToJsonObject(Path path) {
        if (path == null) return null;
        JsonObject out = new JsonObject();
        if (path.toAbsolutePath().startsWith(GAME_DIR)) {
            out.addProperty("relative", true);
            out.addProperty("path", GAME_DIR.relativize(path).toString().replace("\\", "/"));
        } else {
            out.addProperty("relative", false);
            out.addProperty("path", path.toString().replace("\\", "/"));
        }
        return out;
    }
}