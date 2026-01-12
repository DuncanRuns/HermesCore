package me.duncanruns.hermes.core;

import com.google.gson.JsonObject;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

@Mod(HermesCore.MODID)
public class HermesCore {
    public static final String MODID = "hermescore";
    public static final Path GAME_DIR = FMLPaths.GAMEDIR.get();
    public static final Path LOCAL_HERMES_FOLDER = GAME_DIR.resolve("hermes");
    public static final Path GLOBAL_HERMES_FOLDER = getGlobalPath();
    public static Boolean IS_CLIENT;

    public static Boolean isClient() {
        try {
            Method method;
            try {
                method = FMLEnvironment.class.getDeclaredMethod("getDist");
            } catch (NoSuchMethodException e) {
                try {
                    method = FMLLoader.class.getDeclaredMethod("getDist");
                } catch (NoSuchMethodException ex) {
                    return null;
                }
            }
            method.setAccessible(true);
            try {
                return ((Dist) method.invoke(null)).isClient();
            } catch (IllegalAccessException | InvocationTargetException e) {
                return null;
            }
        } catch (Exception e) {
            System.out.println("Failed to determine if client: " + e.getMessage());
            return null;
        }
    }

    public HermesCore(IEventBus modEventBus) {
        IS_CLIENT = isClient();
        if (ModList.get().getModContainerById("hermes").isPresent()) return;
        InstanceInfo.init();
    }

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

    public static long getProcessId() {
        String jvmName = ManagementFactory.getRuntimeMXBean().getName();
        int atIndex = jvmName.indexOf('@');
        if (atIndex > 0) {
            try {
                return Long.parseLong(jvmName.substring(0, atIndex));
            } catch (NumberFormatException e) {
                // Unexpected format, fallback
            }
        }
        throw new IllegalStateException("Unable to determine process ID from JVM name: " + jvmName);
    }

    public static long tryGetProcessId() {
        long pid;
        try {
            pid = getProcessId();
        } catch (Exception e) {
            pid = -1;
        }
        return pid;
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
