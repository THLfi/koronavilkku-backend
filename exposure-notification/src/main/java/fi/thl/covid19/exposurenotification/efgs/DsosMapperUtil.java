package fi.thl.covid19.exposurenotification.efgs;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class DsosMapperUtil {
    public static final int DEFAULT_DAYS_SINCE_SYMPTOMS = 4000;
    private static final Integer DEFAULT_VALUE = null;

    public enum DsosInterpretationMapper {
        SYMPTOMS_WITH_DATE (() -> IntStream.rangeClosed(-14,14), dsos -> dsos),
        SYMPTOMS_WITH_TIME_RANGE (() -> IntStream.rangeClosed(100,1900), dsos -> DEFAULT_VALUE),
        SYMPTOMS_WITHOUT_DATE (() -> IntStream.rangeClosed(1986,2014), dsos -> DEFAULT_VALUE),
        SYMPTOMS_UNKNOWN (() -> IntStream.rangeClosed(3986,4014), dsos -> DEFAULT_VALUE),
        NO_SYMPTOMS (() -> IntStream.rangeClosed(2986,3014), dsos -> DEFAULT_VALUE),
        UNKNOWN (() -> IntStream.rangeClosed(Integer.MAX_VALUE - 1,Integer.MAX_VALUE), dsos -> DEFAULT_VALUE);

        private final Supplier<IntStream> range;
        private final IntFunction<Integer> mapper;

        DsosInterpretationMapper(Supplier<IntStream> range, IntFunction<Integer> mapper) {
            this.range = range;
            this.mapper = mapper;
        }

        public Optional<Integer> apply(Integer dsos) {
            return Optional.ofNullable(mapper.apply(dsos));
        }

        public static Optional<Integer> mapFrom(int dsos) {
           DsosInterpretationMapper mapperInRange = Arrays.stream(values())
                   .filter(e -> e.range.get().anyMatch(i -> i == dsos))
                   .findFirst()
                   .orElse(DsosInterpretationMapper.UNKNOWN);
            return mapperInRange.apply(dsos);
        }
    }
}
