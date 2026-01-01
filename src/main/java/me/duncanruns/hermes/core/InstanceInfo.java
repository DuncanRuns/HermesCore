package me.duncanruns.hermes.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class InstanceInfo {
    public static final Path GLOBAL_FOLDER = HermesCore.GLOBAL_HERMES_FOLDER.resolve("instances");
    public static final Path LOCAL_FOLDER = HermesCore.LOCAL_HERMES_FOLDER.resolve("instances");
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final List<String> disabledFeatures = new ArrayList<>();
    private static Path worldLogPath = null;

    private InstanceInfo() {
    }


    public static void init() {
        ensureGlobalFolder();
        ensureLocalFolder();
        createInstanceInfoFiles();
    }

    private static void createInstanceInfoFiles() {
        long pid = getPid();
        String instanceJson = getInstanceInfoJson(pid);
        writeAndLock(getFilePath(GLOBAL_FOLDER, pid), instanceJson);
        writeAndLock(getFilePath(LOCAL_FOLDER, pid), instanceJson);
    }

    private static void writeAndLock(Path path, String contents) {
        try {
            RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw");
            file.setLength(0);
            file.seek(0);
            file.write(contents.getBytes(StandardCharsets.UTF_8));
            FileLock fileLock = file.getChannel().tryLock(0L, Long.MAX_VALUE, true);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    fileLock.release();
                    file.close();
                    Files.delete(path);
                } catch (Throwable ignored) {
                }
            }, "Hermes Instance Info File Lock Cleanup"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getInstanceInfoJson(long pid) {
        JsonObject instanceJson = new JsonObject();
        if (pid != -1) instanceJson.addProperty("pid", pid);
        if (worldLogPath != null) instanceJson.add("world_log", HermesCore.pathToJsonObject(worldLogPath));
        instanceJson.addProperty("is_server", !HermesCore.IS_CLIENT);
        instanceJson.addProperty("game_dir", FabricLoader.getInstance().getGameDir().toAbsolutePath().toString());
        instanceJson.addProperty("game_version", FabricLoader.getInstance().getModContainer("minecraft").orElseThrow(() -> new IllegalStateException("Failed to find minecraft version via fabric loader")).getMetadata().getVersion().getFriendlyString());
        instanceJson.add("disabled_features", GSON.toJsonTree(disabledFeatures));

        JsonArray modsArray = new JsonArray();
        FabricLoader.getInstance().getAllMods().forEach(modContainer -> {
            JsonObject modObject = new JsonObject();
            modObject.addProperty("name", modContainer.getMetadata().getName());
            modObject.addProperty("id", modContainer.getMetadata().getId());
            modObject.addProperty("version", modContainer.getMetadata().getVersion().getFriendlyString());
            modsArray.add(modObject);
        });
        instanceJson.add("mods", modsArray);
        return GSON.toJson(instanceJson);
    }

    private static Path getFilePath(Path folder, long pid) {
        String fileName;
        if (pid == -1) {
            fileName = "unknown-" + System.currentTimeMillis() + "-" + new Random().nextLong();
        } else {
            fileName = String.valueOf(pid);
        }
        return folder.resolve(fileName + ".json");
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

    private static long getPid() {
        long pid;
        try {
            pid = getProcessId();
        } catch (Exception e) {
            pid = -1;
        }
        return pid;
    }

    private static void ensureGlobalFolder() {
        if (!Files.exists(GLOBAL_FOLDER)) {
            try {
                Files.createDirectories(GLOBAL_FOLDER);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void ensureLocalFolder() {
        if (!Files.exists(LOCAL_FOLDER)) {
            try {
                Files.createDirectories(LOCAL_FOLDER);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void setWorldLogPath(Path worldLogPath) {
        if (InstanceInfo.worldLogPath != null) throw new IllegalStateException("World log path already set");
        InstanceInfo.worldLogPath = worldLogPath;
    }

    public static void addDisabledFeature(String feature) {
        if (disabledFeatures.contains(feature)) return;
        disabledFeatures.add(feature);
    }

}
