package fi.thl.covid19.exposurenotification.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static java.nio.file.StandardOpenOption.READ;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

public final class BatchFileChannel implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(BatchFileChannel.class);

    private final BatchId batchId;
    private final FileChannel channel;
    private final Optional<FileLock> lock;
    private final Set<Long> accessors = new HashSet<>();

    public BatchFileChannel(BatchId batchId, Path pathToFile) {
        this.batchId = batchId;
        try {
            this.channel = FileChannel.open(pathToFile, READ);
            if (channel.size() > Integer.MAX_VALUE) {
                tryClose(channel);
                throw new IllegalStateException("File too large: batchId=" + batchId + " size=" + channel.size());
            }
            this.lock = Optional.ofNullable(channel.tryLock(0, Integer.MAX_VALUE, true));
            if (lock.isEmpty()) {
                throw new IOException("File locking failed: batchId=" + batchId);
            }
        } catch (Exception e) {
            close();
            throw new IllegalStateException("Could not open batch file for reading: " + batchId, e);
        }
    }

    public void addAccessor(Long accessor) {
        this.accessors.add(accessor);
    }

    public boolean removeAccessor(Long accessor) {
        this.accessors.remove(accessor);
        return this.accessors.isEmpty();
    }

    public byte[] read() {
        try {
            ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
            channel.read(buffer);
            return buffer.array();
        } catch (IOException e) {
            throw new UncheckedIOException("Batch file read failed: " + batchId, e);
        }
    }

    @Override
    public void close() {
        lock.ifPresent(this::tryClose);
        tryClose(channel);
    }

    private void tryClose(AutoCloseable c) {
        try {
            c.close();
        } catch (Exception e) {
            LOG.warn("Failed to close resource in batch file read: {} {}",
                    keyValue("batchId", batchId),
                    keyValue("type", c.getClass().getSimpleName()));
        }
    }
}
