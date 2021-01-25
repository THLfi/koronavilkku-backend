package fi.thl.covid19.exposurenotification.efgs.util;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.function.Predicate;

public class DsosMapperUtil {
    public static final int DEFAULT_DAYS_SINCE_SYMPTOMS = 4000;
    public static final int DEFAULT_LOCAL_DAYS_SINCE_SYMPTOMS = 0;
    private static final Integer DEFAULT_VALUE = null;

    public enum DsosInterpretationMapper {
        SYMPTOMS_WITH_DATE(dsos -> -14 <= dsos && dsos <= 14, dsos -> dsos, 0),
        SYMPTOMS_WITH_TIME_RANGE(dsos -> 100 <= dsos && dsos <= 1900, dsos -> DEFAULT_VALUE, 1400),
        SYMPTOMS_WITHOUT_DATE(dsos -> 1986 <= dsos && dsos <= 2014, dsos -> DEFAULT_VALUE, 2000),
        SYMPTOMS_UNKNOWN(dsos -> 3986 <= dsos && dsos <= 4014, dsos -> DEFAULT_VALUE, 4000),
        NO_SYMPTOMS(dsos -> 2986 <= dsos && dsos <= 3014, dsos -> DEFAULT_VALUE, 3000),
        UNKNOWN(dsos -> Integer.MAX_VALUE == dsos, dsos -> DEFAULT_VALUE, DEFAULT_LOCAL_DAYS_SINCE_SYMPTOMS);

        private final Predicate<Integer> range;
        private final IntFunction<Integer> mapper;
        private final int zero;

        DsosInterpretationMapper(Predicate<Integer> range, IntFunction<Integer> mapper, int zero) {
            this.range = range;
            this.mapper = mapper;
            this.zero = zero;
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

        public static int mapToEfgs(Optional<Integer> localDsos, Optional<Boolean> symptomsExists) {
            final int dsos = localDsos.orElse(DEFAULT_LOCAL_DAYS_SINCE_SYMPTOMS);
            AtomicInteger efgsDsos = new AtomicInteger(DEFAULT_LOCAL_DAYS_SINCE_SYMPTOMS);
            symptomsExists.ifPresentOrElse(
                    e -> efgsDsos.set(mapDsos(dsos, e)),
                    () -> efgsDsos.set(SYMPTOMS_UNKNOWN.zero + dsos));
            return efgsDsos.get();
        }

        private static int mapDsos(int dsos, boolean symptomsExists) {
            if (symptomsExists) {
                return SYMPTOMS_WITH_DATE.zero + dsos;
            } else {
                return NO_SYMPTOMS.zero + dsos;
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
