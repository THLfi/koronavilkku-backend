package fi.thl.covid19.exposurenotification.configuration.v2;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Configuration supporting exposure windows (or V2) api of exposure notifications
// See more from https://developers.google.com/android/exposure-notifications/exposure-notifications-api
public class ExposureConfigurationV2 {

    public final int version;

    // DailySummariesConfig
    public final Map<String, Double> reportTypeWeights;
    public final Map<String, Double> infectiousnessWeights;
    public final List<BigDecimal> attenuationBucketThresholdDb;
    public final List<BigDecimal> attenuationBucketWeights;
    public final int daysSinceExposureThreshold;
    public final double minimumWindowScore;

    // DiagnosisKeysDataMapping
    public final Map<Integer, String> daysSinceOnsetToInfectiousness;
    public final String infectiousnessWhenDaysSinceOnsetMissing;

    /*
     * Set of available countries in ISO-3166 alpha-2 format
     * Source: https://ec.europa.eu/info/live-work-travel-eu/health/coronavirus-response/travel-during-coronavirus-pandemic/mobile-contact-tracing-apps-eu-member-states_en
     */
    public final Set<String> availableCountries;

    public ExposureConfigurationV2(
            int version,
            Map<String, Double> reportTypeWeights,
            Map<String, Double> infectiousnessWeights,
            List<BigDecimal> attenuationBucketThresholdDb,
            List<BigDecimal> attenuationBucketWeights,
            int daysSinceExposureThreshold,
            double minimumWindowScore,
            Map<Integer, String> daysSinceOnsetToInfectiousness,
            String infectiousnessWhenDaysSinceOnsetMissing,
            Set<String> availableCountries
    ) {
        this.version = version;
        this.reportTypeWeights = reportTypeWeights;
        this.infectiousnessWeights = infectiousnessWeights;
        this.attenuationBucketThresholdDb = attenuationBucketThresholdDb;
        this.attenuationBucketWeights = attenuationBucketWeights;
        this.daysSinceExposureThreshold = daysSinceExposureThreshold;
        this.minimumWindowScore = minimumWindowScore;
        this.daysSinceOnsetToInfectiousness = daysSinceOnsetToInfectiousness;
        this.infectiousnessWhenDaysSinceOnsetMissing = infectiousnessWhenDaysSinceOnsetMissing;
        this.availableCountries = availableCountries;
    }
}
