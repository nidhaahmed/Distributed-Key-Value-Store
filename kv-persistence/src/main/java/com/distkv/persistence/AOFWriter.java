package com.distkv.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe Append-Only File (AOF) logger.
 * Sequentially appends every mutation (SET, DEL, CLEAR) to disk with configurable fsync policies.
 */
public class AOFWriter implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AOFWriter.class);

    private final File aofFile;
    private final FsyncPolicy fsyncPolicy;
    private final FileOutputStream fos;
    private final FileChannel channel;
    private final ScheduledExecutorService syncExecutor;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public AOFWriter(File aofFile, FsyncPolicy fsyncPolicy) throws IOException {
        this.aofFile = aofFile;
        this.fsyncPolicy = fsyncPolicy;

        File parent = aofFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        this.fos = new FileOutputStream(aofFile, true);
        this.channel = fos.getChannel();

        if (fsyncPolicy == FsyncPolicy.EVERYSEC) {
            this.syncExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "aof-fsync-worker");
                t.setDaemon(true);
                return t;
            });
            this.syncExecutor.scheduleAtFixedRate(this::fsyncSafely, 1, 1, TimeUnit.SECONDS);
        } else {
            this.syncExecutor = null;
        }

        log.info("AOFWriter initialized: file={}, fsyncPolicy={}", aofFile.getAbsolutePath(), fsyncPolicy);
    }

    public synchronized void appendSet(String key, String value) throws IOException {
        ensureOpen();
        String entry = "SET " + escape(key) + " " + escape(value) + "\n";
        writeEntry(entry);
    }

    public synchronized void appendDelete(String key) throws IOException {
        ensureOpen();
        String entry = "DEL " + escape(key) + "\n";
        writeEntry(entry);
    }

    public synchronized void appendClear() throws IOException {
        ensureOpen();
        String entry = "CLEAR\n";
        writeEntry(entry);
    }

    private void writeEntry(String entry) throws IOException {
        byte[] bytes = entry.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }

        if (fsyncPolicy == FsyncPolicy.ALWAYS) {
            channel.force(true);
        }
    }

    public synchronized void fsync() throws IOException {
        ensureOpen();
        channel.force(true);
    }

    private void fsyncSafely() {
        try {
            if (!closed.get() && channel.isOpen()) {
                channel.force(false);
            }
        } catch (IOException e) {
            log.warn("Background AOF fsync failed: {}", e.getMessage());
        }
    }

    private void ensureOpen() throws IOException {
        if (closed.get() || !channel.isOpen()) {
            throw new IOException("AOFWriter is closed");
        }
    }

    public static String escape(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    public File getFile() {
        return aofFile;
    }

    public FsyncPolicy getFsyncPolicy() {
        return fsyncPolicy;
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            if (syncExecutor != null) {
                syncExecutor.shutdown();
                try {
                    if (!syncExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                        syncExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    syncExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }

            try {
                if (channel.isOpen()) {
                    channel.force(true);
                    channel.close();
                }
            } finally {
                fos.close();
                log.info("AOFWriter closed cleanly: {}", aofFile.getName());
            }
        }
    }
}
