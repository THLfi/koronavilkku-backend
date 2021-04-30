package fi.thl.covid19.exposurenotification.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static net.logstash.logback.argument.StructuredArguments.keyValue;

@Service
public class BatchFileStorage {

    private static final Logger LOG = LoggerFactory.getLogger(BatchFileStorage.class);

    private static final String DIRECTORY = "diagnosis-files";

    final Path batchFileDirectory;

    public BatchFileStorage(@Value("${covid19.diagnosis.file-storage.directory:}") Optional<String> directory) {
        this.batchFileDirectory = getDirectory(directory.map(String::trim).filter(s -> !s.isEmpty()));
        LOG.info("Initialized: {} '{}'",
                keyValue("temp", directory.isEmpty()),
                keyValue("directory", this.batchFileDirectory));
    }

    private static Path getDirectory(Optional<String> directory) {
        try {
            if (directory.isPresent()) {
                return verifyDirectory(directory.get() + File.separator + DIRECTORY);
            } else {
                return Files.createTempDirectory(DIRECTORY);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path verifyDirectory(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Could not initialize batch file directory: " + dirPath);
        } else if (!dir.isDirectory()) {
            throw new IllegalStateException("Another file is blocking batch file directory creation: " + dirPath);
        } else if (!dir.canWrite()) {
            throw new IllegalStateException("Batch-file directory is not writable: " + dirPath);
        }
        return dir.toPath();
    }

    public boolean fileExists(BatchId id) {
        return Files.exists(pathToFile(id));
    }

    public int deleteKeyBatchesBefore(int interval) {
        int filesRemoved = 0;
        for (BatchId id : listBatchesOnDisk()) {
            if (id.intervalNumber < interval && tryDelete(id)) {
                filesRemoved++;
            }
        }
        return filesRemoved;
    }

    private List<BatchId> listBatchesOnDisk() {
        try (Stream<Path> stream = Files.walk(batchFileDirectory, 1)) {
            return stream
                    .filter(p -> !Files.isDirectory(p))
                    .map(p -> p.getFileName().toString())
                    .filter(BatchFile::isBatchFileName)
                    .map(BatchFile::toBatchId)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            LOG.error("Failed to list files on disk");
            throw new UncheckedIOException(e);
        }
    }

    private boolean tryDelete(BatchId id) {
        LOG.info("Deleting batch: {}", keyValue("batchId", id));
        try {
            return Files.deleteIfExists(pathToFile(id));
        } catch (IOException e) {
            LOG.warn("Failed to delete file: {}", keyValue("batchId", id));
            throw new UncheckedIOException(e);
        }
    }

    public void addBatchFile(BatchId batchId, byte[] data) {
        try (FileChannel channel = FileChannel.open(pathToFile(batchId), CREATE, WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock != null) {
                channel.truncate(0);
                channel.write(ByteBuffer.wrap(data));
                LOG.info("Wrote new batch: {}", keyValue("batchId", batchId));
            } else {
                LOG.info("Overlapping write (another process is writing the batch): {}", keyValue("batchId", batchId));
            }
        } catch (IOException e) {
            LOG.error("Error writing batch file: {}", keyValue("batchId", batchId));
            throw new UncheckedIOException(e);
        }
    }

    private final AtomicLong readOperationId = new AtomicLong(0);
    public Optional<byte[]> readBatchFile(BatchId batchId) {
        long operationId = readOperationId.incrementAndGet();
        try {
            BatchFileChannel channel = openFileWithLock(batchId, operationId);
            return Optional.of(channel.read());
        } catch (Exception e) {
            LOG.info("Could not read file from disk.", e);
            return Optional.empty();
        } finally {
            releaseFileLock(batchId, operationId);
        }
    }

    private Path pathToFile(BatchId batchId) {
        return batchFileDirectory.resolve(BatchFile.batchFileName(batchId));
    }

    private final Map<BatchId, BatchFileChannel> channels = new HashMap<>();
    private BatchFileChannel openFileWithLock(BatchId batchId, Long accessor) {
        synchronized (channels) {
            Path path = pathToFile(batchId);
            BatchFileChannel channel = channels.computeIfAbsent(batchId, id -> new BatchFileChannel(batchId, path));
            channel.addAccessor(accessor);
            return channel;
        }
    }

    private void releaseFileLock(BatchId batchId, Long accessor) {
        synchronized (channels) {
            BatchFileChannel lock = channels.get(batchId);
            if (lock != null && lock.removeAccessor(accessor)) {
                channels.remove(batchId);
                lock.close();
            }
        }
    }

}
