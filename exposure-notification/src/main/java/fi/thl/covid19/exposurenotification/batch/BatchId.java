package fi.thl.covid19.exposurenotification.batch;

import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class BatchId implements Comparable<BatchId> {

    private static final String SEPARATOR = "_";
    public static final BatchId DEFAULT = new BatchId(0);

    public final int intervalNumber;
    public final Optional<Integer> intervalNumberV2;

    public BatchId(int intervalNumber) {
        this(intervalNumber, Optional.empty());
    }
    public BatchId(int intervalNumber, Optional<Integer> intervalNumberV2) {
        if (intervalNumber < 0 || (intervalNumberV2.isPresent() && intervalNumberV2.get() < 0)) {
            String formatted = intervalNumber + intervalNumberV2.map(n -> "_" + n).orElse("");
            throw new IllegalArgumentException("Batch ID out of range: " + formatted);
        }
        this.intervalNumber = intervalNumber;
        this.intervalNumberV2 = requireNonNull(intervalNumberV2);
    }

    public BatchId(String idString) {
        String cleaned = requireNonNull(idString, "The batch ID cannot be null.").trim();
        if (cleaned.contains(SEPARATOR) && cleaned.length() <= 30) {
            String[] pieces = cleaned.split(SEPARATOR);
            if (pieces.length != 2) throw new IllegalArgumentException("Invalid Demo or V2 Batch ID: parts=" + pieces.length);
            this.intervalNumber = Integer.parseInt(pieces[0]);
            this.intervalNumberV2 = Optional.of(Integer.parseInt(pieces[1]));
        } else if (cleaned.length() > 0 && cleaned.length() <= 20) {
            this.intervalNumber = Integer.parseInt(cleaned);
            this.intervalNumberV2 = Optional.empty();
        } else {
            throw new IllegalArgumentException("Invalid Batch ID: length=" + cleaned.length());
        }
    }

    public boolean isDemoOrV2Batch() {
        return intervalNumberV2.isPresent();
    }

    public boolean isBefore(BatchId other) {
        return compareTo(other) < 0;
    }

    public boolean isAfter(BatchId other) {
        return compareTo(other) > 0;
    }

    @Override
    public String toString() {
        return this.intervalNumber + intervalNumberV2.map(n -> SEPARATOR + n).orElse("");
    }

    @Override
    public int compareTo(BatchId o) {
        int main = Long.compare(intervalNumber, o.intervalNumber);
        if (main != 0) {
            return main;
        } else {
            return intervalNumberV2.orElse(Integer.MAX_VALUE).compareTo(o.intervalNumberV2.orElse(Integer.MAX_VALUE));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BatchId batchId = (BatchId) o;
        return intervalNumber == batchId.intervalNumber &&
                intervalNumberV2.equals(batchId.intervalNumberV2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(intervalNumber, intervalNumberV2);
    }
}
