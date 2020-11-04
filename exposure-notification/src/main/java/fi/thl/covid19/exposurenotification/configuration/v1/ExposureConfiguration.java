package fi.thl.covid19.exposurenotification.configuration.v1;

/**
 * Source:
 * https://static.googleusercontent.com/media/www.google.com/en//covid19/exposurenotifications/pdfs/Android-Exposure-Notification-API-documentation-v1.3.2.pdf
 */

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Exposure configuration parameters that can be provided when initializing the
 * service.
 * <p>
 * These parameters are used to calculate risk for each exposure incident using
 * the following formula:
 *
 * <p><code>
 * RiskScore = attenuationScore
 * * daysSinceLastExposureScore
 * * durationScore
 * * transmissionRiskScore
 * </code>
 *
 * <p>Scores are in the range 0-8. Weights are in the range 0-100.
 */

public class ExposureConfiguration {
    public final int version;

    /**
     * Minimum risk score. Excludes exposure incidents with scores lower than this.
     * Defaults to no minimum.
     */
    public final int minimumRiskScore;

    /**
     * Scores for attenuation buckets. Must contain 8 scores, one for each bucket
     * as defined below:
     *
     * <p><code>{@code
     * attenuationScores[0] when Attenuation > 73
     * attenuationScores[1] when 73 >= Attenuation > 63
     * attenuationScores[2] when 63 >= Attenuation > 51
     * attenuationScores[3] when 51 >= Attenuation > 33
     * attenuationScores[4] when 33 >= Attenuation > 27
     * attenuationScores[5] when 27 >= Attenuation > 15
     * attenuationScores[6] when 15 >= Attenuation > 10
     * attenuationScores[7] when 10 >= Attenuation
     * }</code>
     */
    public final List<Integer> attenuationScores;

    /**
     * Scores for days since last exposure buckets. Must contain 8 scores, one for
     * each bucket as defined below:
     *
     * <p><code>{@code
     * daysSinceLastExposureScores[0] when Days >= 14
     * daysSinceLastExposureScores[1] when Days >= 12
     * daysSinceLastExposureScores[2] when Days >= 10
     * daysSinceLastExposureScores[3] when Days >= 8
     * daysSinceLastExposureScores[4] when Days >= 6
     * daysSinceLastExposureScores[5] when Days >= 4
     * daysSinceLastExposureScores[6] when Days >= 2
     * daysSinceLastExposureScores[7] when Days >= 0
     * }</code>
     */

    public final List<Integer> daysSinceLastExposureScores;

    /**
     * Scores for duration buckets. Must contain 8 scores, one for each bucket as
     * defined below:
     *
     * <p><code>{@code
     * durationScores[0] when Duration == 0
     * durationScores[1] when Duration <= 5
     * durationScores[2] when Duration <= 10
     * durationScores[3] when Duration <= 15
     * durationScores[4] when Duration <= 20
     * durationScores[5] when Duration <= 25
     * durationScores[6] when Duration <= 30
     * durationScores[7] when Duration  > 30
     * }</code>
     */
    public final List<Integer> durationScores;

    /**
     * Scores for transmission risk buckets. Must contain 8 scores, one for each
     * bucket as defined below:
     *
     * <p><code>{@code
     * transmissionRiskScores[0] when RISK_SCORE_LOWEST
     * transmissionRiskScores[1] when RISK_SCORE_LOW
     * transmissionRiskScores[2] when RISK_SCORE_LOW_MEDIUM
     * transmissionRiskScores[3] when RISK_SCORE_MEDIUM
     * transmissionRiskScores[4] when RISK_SCORE_MEDIUM_HIGH
     * transmissionRiskScores[5] when RISK_SCORE_HIGH
     * transmissionRiskScores[6] when RISK_SCORE_VERY_HIGH
     * transmissionRiskScores[7] when RISK_SCORE_HIGHEST
     * }</code>
     */
    public final List<Integer> transmissionRiskScores;

    /**
     * IOS and Android index this array differently:
     * <li>
     *     <ul>iOS handles it as defined above, indexing the array with the risk level (0-7)</ul>
     *     <ul>Android interprets risk level 0 as "not used" giving score multiplier of 1. Levels 1-8 are used as 1-based indices (so scores[level-1])</ul>
     * </li>
     * <p>
     * Our levels are defined as 0-7 like iOS, but the extreme levels (0 & 7) give score 0.
     * Hence, as a workaround, we filter out those 0-score keys and provide a separate array of parameters for android, shifting scores by one index.
     */
    public final List<Integer> transmissionRiskScoresAndroid;

    /**
     * Attenuation thresholds to apply when calculating duration at attenuation. Must
     * contain two thresholds, each in range of 0 - 255.
     * durationAtAttenuationThresholds[0] has to be <=
     * durationAtAttenuationThresholds[1]. These are used used to populate {@link
     * ExposureSummary#getAttenuationDurationsInMinutes} and {@link
     * ExposureInformation#getAttenuationDurationsInMinutes}.
     */
    public final List<Integer> durationAtAttenuationThresholds;

    /*
     *  Weight multipliers for different attenuation buckets
     */
    public final List<BigDecimal> durationAtAttenuationWeights;

    /*
     *  Minimun combined duration of exposures used in calculations. This is is used in combination with durationAtAttenuationWeights.
     */
    public final int exposureRiskDuration;

    public ExposureConfiguration(
            int version,
            int minimumRiskScore,
            List<Integer> attenuationScores,
            List<Integer> daysSinceLastExposureScores,
            List<Integer> durationScores,
            List<Integer> transmissionRiskScores,
            List<Integer> durationAtAttenuationThresholds,
            List<BigDecimal> durationAtAttenuationWeights,
            int exposureRiskDuration
    ) {
        this.version = version;
        this.minimumRiskScore = minimumRiskScore;
        this.attenuationScores =
                assertParams(8, "attenuationScores", attenuationScores);
        this.daysSinceLastExposureScores =
                assertParams(8, "daysSinceLastExposureScores", daysSinceLastExposureScores);
        this.durationScores =
                assertParams(8, "durationScores", durationScores);
        this.transmissionRiskScores =
                assertParams(8, "transmissionRiskScores", transmissionRiskScores);
        // Workaround for different transmission risk handling on Android: shift score table by one index (losing the first value)
        this.transmissionRiskScoresAndroid = Stream.concat(transmissionRiskScores.stream().skip(1), Stream.of(0))
                .collect(Collectors.toList());
        this.durationAtAttenuationThresholds =
                assertParams(2, "durationAtAttenuationThresholds", durationAtAttenuationThresholds);
        this.durationAtAttenuationWeights =
                assertParams(3, "durationAtAttenuationWeights", durationAtAttenuationWeights);
        this.exposureRiskDuration = exposureRiskDuration;
    }

    private <T extends Number> List<T> assertParams(int size, String name, List<T> params) {
        requireNonNull(params, name + " should exist");
        if (params.size() != size) {
            throw new IllegalArgumentException(name + " array size should be " + size + " (was " + params.size() + ")");
        }
        return params;
    }
}
