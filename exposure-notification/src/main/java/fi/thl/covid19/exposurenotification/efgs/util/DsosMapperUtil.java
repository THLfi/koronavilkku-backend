package fi.thl.covid19.exposurenotification.efgs.util;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Predicate;

public class DsosMapperUtil {
    public static final int DEFAULT_DAYS_SINCE_SYMPTOMS = 4000;
    private static final Integer DEFAULT_VALUE = null;

    public enum DsosInterpretationMapper {
        SYMPTOMS_WITH_DATE(dsos -> -14 <= dsos && dsos <= 14, dsos -> dsos),
        SYMPTOMS_WITH_TIME_RANGE(dsos -> 100 <= dsos && dsos <= 1900, dsos -> DEFAULT_VALUE),
        SYMPTOMS_WITHOUT_DATE(dsos -> 1986 <= dsos && dsos <= 2014, dsos -> DEFAULT_VALUE),
        SYMPTOMS_UNKNOWN(dsos -> 3986 <= dsos && dsos <= 4014, dsos -> DEFAULT_VALUE),
        NO_SYMPTOMS(dsos -> 2986 <= dsos && dsos <= 3014, dsos -> DEFAULT_VALUE),
        UNKNOWN(dsos -> Integer.MAX_VALUE == dsos, dsos -> DEFAULT_VALUE);

        private final Predicate<Integer> range;
        private final IntFunction<Integer> mapper;

        DsosInterpretationMapper(Predicate<Integer> range, IntFunction<Integer> mapper) {
            this.range = range;
            this.mapper = mapper;
        }

        public Optional<Integer> apply(Integer dsos) {
            return Optional.ofNullable(mapper.apply(dsos));
        }

        public static Optional<Integer> mapFrom(int dsos) {
            return mapperInRange(dsos).apply(dsos);
        }

        public static Optional<Boolean> symptomsExists(int dsos) {
            DsosInterpretationMapper mapper = mapperInRange(dsos);

            if (SYMPTOMS_UNKNOWN.equals(mapper)) {
                return Optional.empty();
            } else {
                return Optional.of(!NO_SYMPTOMS.equals(mapper));
            }
        }

        private static DsosInterpretationMapper mapperInRange(int dsos) {
            return Arrays.stream(values())
                    .filter(e -> e.range.test(dsos))
                    .findFirst()
                    .orElse(DsosInterpretationMapper.UNKNOWN);
        }
    }
}
