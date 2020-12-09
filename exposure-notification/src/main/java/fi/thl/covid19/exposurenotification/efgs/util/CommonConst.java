package fi.thl.covid19.exposurenotification.efgs.util;

public class CommonConst {
    public enum EfgsOperationState {STARTED, FINISHED, ERROR}

    public static final long STALLED_MIN_AGE_IN_MINUTES = 10;
    public static final int MAX_RETRY_COUNT = 3;
}
