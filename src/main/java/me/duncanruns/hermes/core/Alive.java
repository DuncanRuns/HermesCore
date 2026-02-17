package me.duncanruns.hermes.core;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class Alive {
    private static final Path PATH = HermesCore.LOCAL_HERMES_FOLDER.resolve("alive").normalize();

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        thread.setName("Hermes-Alive");
        return thread;
    });

    private static RandomAccessFile file = null;
    private static long pid;

    private static boolean closing = false;

    private Alive() {
    }

    public static void init() {
        pid = HermesCore.tryGetProcessId();
        EXECUTOR.scheduleAtFixedRate(Alive::tick, 0, 1, java.util.concurrent.TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(Alive::close, "Hermes-Alive-Close"));
    }

    private static void tick() {
        if (closing) return;
        if (file == null) {
            tryCreate();
        } else {
            tickAlive();
        }
    }

    private static void tickAlive() {
        long now = System.currentTimeMillis();
        try {
            file.seek(8);
            file.writeLong(now);
            file.getChannel().force(false);
        } catch (Exception e) {
            System.out.println("Failed to write alive file: " + e.getMessage());
            close();
        }
    }

    private static void tryCreate() {
        if (!Files.isDirectory(HermesCore.LOCAL_HERMES_FOLDER.getParent())) return;
        if (!Files.isDirectory(HermesCore.LOCAL_HERMES_FOLDER)) {
            try {
                Files.createDirectories(HermesCore.LOCAL_HERMES_FOLDER);
            } catch (Exception e) {
                HermesCore.ERROR_LOGGER.accept("Failed to create Hermes folder.", e);
                EXECUTOR.shutdown();
                return;
            }
        }
        try {
            file = new RandomAccessFile(PATH.toFile(), "rw");
            file.setLength(0);
            file.writeLong(pid);
            tickAlive();
        } catch (IOException e) {
            HermesCore.ERROR_LOGGER.accept("Failed to create alive file.", e);
            EXECUTOR.shutdown();
        }
    }

    private static void close() {
        closing = true;
        EXECUTOR.shutdown();
        if (file != null) {
            try {
                file.seek(8);
                file.writeLong(-1);
                file.close();
            } catch (IOException e) {
                HermesCore.ERROR_LOGGER.accept("Failed to close alive file.", e);
            }
        }
    }

}
