package fi.thl.covid19.exposurenotification.batch;

import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class BatchId implements Comparable<BatchId> {

    private static final String SEPARATOR = "_";
    public static final BatchId DEFAULT = new BatchId(0);

    public final int intervalNumber;
    public final Optional<Integer> demoBatchNumber;

    public BatchId(int intervalNumber) {
        this(intervalNumber, Optional.empty());
    }
    public BatchId(int intervalNumber, Optional<Integer> demoBatchNumber) {
        if (intervalNumber < 0 || (demoBatchNumber.isPresent() && demoBatchNumber.get() < 0)) {
            String formatted = intervalNumber + demoBatchNumber.map(n -> "_" + n).orElse("");
            throw new IllegalArgumentException("Batch ID out of range: " + formatted);
        }
        this.intervalNumber = intervalNumber;
        this.demoBatchNumber = requireNonNull(demoBatchNumber);
    }

    public BatchId(String idString) {
        String cleaned = requireNonNull(idString, "The batch ID cannot be null.").trim();
        if (cleaned.contains(SEPARATOR) && cleaned.length() <= 30) {
            String[] pieces = cleaned.split(SEPARATOR);
            if (pieces.length != 2) throw new IllegalArgumentException("Invalid Demo Batch ID: parts=" + pieces.length);
            this.intervalNumber = Integer.parseInt(pieces[0]);
            this.demoBatchNumber = Optional.of(Integer.parseInt(pieces[1]));
        } else if (cleaned.length() > 0 && cleaned.length() <= 20) {
            this.intervalNumber = Integer.parseInt(cleaned);
            this.demoBatchNumber = Optional.empty();
        } else {
            throw new IllegalArgumentException("Invalid Batch ID: length=" + cleaned.length());
        }
    }

    public boolean isDemoBatch() {
        return demoBatchNumber.isPresent();
    }

    public boolean isBefore(BatchId other) {
        return compareTo(other) < 0;
    }

    public boolean isAfter(BatchId other) {
        return compareTo(other) > 0;
    }

    @Override
    public String toString() {
        return this.intervalNumber + demoBatchNumber.map(n -> SEPARATOR + n).orElse("");
    }

    @Override
    public int compareTo(BatchId o) {
        int main = Long.compare(intervalNumber, o.intervalNumber);
        if (main != 0) {
            return main;
        } else {
            return demoBatchNumber.orElse(Integer.MAX_VALUE).compareTo(o.demoBatchNumber.orElse(Integer.MAX_VALUE));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BatchId batchId = (BatchId) o;
        return intervalNumber == batchId.intervalNumber &&
                demoBatchNumber.equals(batchId.demoBatchNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(intervalNumber, demoBatchNumber);
    }
}
