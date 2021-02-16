package fi.thl.covid19.exposurenotification.batch;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class BatchIdTest {

    @Test
    public void negativeBatchIdIsNotAllowed() {
        assertThrows(IllegalArgumentException.class, () -> new BatchId(-1));
    }

    @Test
    public void negativeDemoIdIsNotAllowed() {
        assertThrows(IllegalArgumentException.class, () -> new BatchId(1, Optional.of(-1)));
    }

    @Test
    public void demoAndV2BatchIdStringFormatIsCorrect() {
        BatchId id = new BatchId(123, Optional.of(45));
        String stringId = "123_45";
        assertEquals(stringId, id.toString());
        assertEquals(id, new BatchId(stringId));
    }

    @Test
    public void batchIdStringFormatIsCorrect() {
        BatchId id = new BatchId(123);
        String stringId = "123";
        assertEquals(stringId, id.toString());
        assertEquals(id, new BatchId(stringId));
    }

    @Test
    public void demoAndV2BatchIsRecognized() {
        assertTrue(new BatchId(23, Optional.of(67)).isDemoOrV2Batch());
        assertTrue(new BatchId("345_21").isDemoOrV2Batch());

        assertFalse(new BatchId(23).isDemoOrV2Batch());
        assertFalse(new BatchId(23, Optional.empty()).isDemoOrV2Batch());
        assertFalse(new BatchId("345").isDemoOrV2Batch());
    }

    @Test
    public void batchOrderingIsCorrect() {
        assertEquals(-1, new BatchId(1).compareTo(new BatchId(2)));
        assertEquals(0, new BatchId(2).compareTo(new BatchId(2)));
        assertEquals(1, new BatchId(3).compareTo(new BatchId(2)));

        assertEquals(-1, new BatchId(1, Optional.of(1)).compareTo(new BatchId(1, Optional.of(2))));
        assertEquals(0, new BatchId(1, Optional.of(2)).compareTo(new BatchId(1, Optional.of(2))));
        assertEquals(1, new BatchId(1, Optional.of(3)).compareTo(new BatchId(1, Optional.of(2))));

        assertEquals(1, new BatchId(1).compareTo(new BatchId(1, Optional.of(1))));
        assertEquals(-1, new BatchId(1, Optional.of(1)).compareTo(new BatchId(1)));
    }
}
