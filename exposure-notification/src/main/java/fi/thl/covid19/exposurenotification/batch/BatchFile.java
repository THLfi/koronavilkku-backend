package fi.thl.covid19.exposurenotification.batch;

import static java.util.Objects.requireNonNull;

public class BatchFile {

    public static final String FILE_PREFIX = "batch_";
    public static final String ZIP_POSTFIX = ".zip";

    public final BatchId id;
    public final byte[] data;

    public BatchFile(BatchId id, byte[] data) {
        this.id = requireNonNull(id);
        this.data = requireNonNull(data);
    }

    public String getName() {
        return batchFileName(id);
    }

    public static String batchFileName(BatchId batchId) {
        return FILE_PREFIX + batchId + ZIP_POSTFIX;
    }

    public static boolean isBatchFileName(String name) {
        return name.startsWith(FILE_PREFIX) &&
                name.endsWith(ZIP_POSTFIX) &&
                name.length() > FILE_PREFIX.length() + ZIP_POSTFIX.length();
    }

    public static BatchId toBatchId(String name) {
        if (isBatchFileName(name)) {
            int startIdx = FILE_PREFIX.length();
            int endIdx = name.length() - ZIP_POSTFIX.length();
            String idStr = name.substring(startIdx, endIdx);
            return new BatchId(idStr);
        } else {
            throw new IllegalStateException("Not a diagnosis batch file: " + name);
        }
    }
}
