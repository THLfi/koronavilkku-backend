package fi.thl.covid19.exposurenotification.batch;

import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class BatchId implements Comparable<BatchId> {

    private static final String SEPARATOR = "_";
    public static final BatchId DEFAULT = new BatchId(0);

    public final int intervalNumber;
    public final Optional<Integer> dailyBatchNumber;

    public BatchId(int intervalNumber) {
        this(intervalNumber, Optional.empty());
    }
    public BatchId(int intervalNumber, Optional<Integer> dailyBatchNumber) {
        if (intervalNumber < 0 || (dailyBatchNumber.isPresent() && dailyBatchNumber.get() < 0)) {
            String formatted = intervalNumber + dailyBatchNumber.map(n -> "_" + n).orElse("");
            throw new IllegalArgumentException("Batch ID out of range: " + formatted);
        }
        this.intervalNumber = intervalNumber;
        this.dailyBatchNumber = requireNonNull(dailyBatchNumber);
    }

    public BatchId(String idString) {
        String cleaned = requireNonNull(idString, "The batch ID cannot be null.").trim();
        if (cleaned.contains(SEPARATOR) && cleaned.length() <= 30) {
            String[] pieces = cleaned.split(SEPARATOR);
            if (pieces.length != 2) throw new IllegalArgumentException("Invalid Demo or V2 Batch ID: parts=" + pieces.length);
            this.intervalNumber = Integer.parseInt(pieces[0]);
            this.dailyBatchNumber = Optional.of(Integer.parseInt(pieces[1]));
        } else if (cleaned.length() > 0 && cleaned.length() <= 20) {
            this.intervalNumber = Integer.parseInt(cleaned);
            this.dailyBatchNumber = Optional.empty();
        } else {
            throw new IllegalArgumentException("Invalid Batch ID: length=" + cleaned.length());
        }
    }

    public boolean isDemoBatch() {
        return dailyBatchNumber.isPresent();
    }

    public boolean isBefore(BatchId other) {
        return compareTo(other) < 0;
    }

    public boolean isAfter(BatchId other) {
        return compareTo(other) > 0;
    }

    @Override
    public String toString() {
        return this.intervalNumber + dailyBatchNumber.map(n -> SEPARATOR + n).orElse("");
    }

    @Override
    public int compareTo(BatchId o) {
        int main = Long.compare(intervalNumber, o.intervalNumber);
        if (main != 0) {
            return main;
        } else {
            return dailyBatchNumber.orElse(Integer.MAX_VALUE).compareTo(o.dailyBatchNumber.orElse(Integer.MAX_VALUE));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BatchId batchId = (BatchId) o;
        return intervalNumber == batchId.intervalNumber &&
                dailyBatchNumber.equals(batchId.dailyBatchNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(intervalNumber, dailyBatchNumber);
    }
}
