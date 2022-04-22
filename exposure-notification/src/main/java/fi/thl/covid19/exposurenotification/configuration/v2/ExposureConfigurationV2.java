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
    public final BigDecimal reportTypeWeightConfirmedTest;
    public final BigDecimal reportTypeWeightConfirmedClinicalDiagnosis;
    public final BigDecimal reportTypeWeightSelfReport;
    public final BigDecimal reportTypeWeightRecursive;
    public final BigDecimal infectiousnessWeightStandard;
    public final BigDecimal infectiousnessWeightHigh;
    public final List<BigDecimal> attenuationBucketThresholdDb;
    public final List<BigDecimal> attenuationBucketWeights;
    public final int daysSinceExposureThreshold;
    public final double minimumWindowScore;
    public final int minimumDailyScore;

    // DiagnosisKeysDataMapping
    public final Map<Integer, String> daysSinceOnsetToInfectiousness;
    public final String infectiousnessWhenDaysSinceOnsetMissing;

    // End of life configurations
    public final boolean endOfLifeReached;
    public final List<EndOfLifeStatistic> endOfLifeStatistics;

    /*
     * Set of available countries in ISO-3166 alpha-2 format
     * Source: https://ec.europa.eu/info/live-work-travel-eu/health/coronavirus-response/travel-during-coronavirus-pandemic/mobile-contact-tracing-apps-eu-member-states_en
     */
    public final Set<String> availableCountries;

    public ExposureConfigurationV2(
            int version,
            BigDecimal reportTypeWeightConfirmedTest,
            BigDecimal reportTypeWeightConfirmedClinicalDiagnosis,
            BigDecimal reportTypeWeightSelfReport,
            BigDecimal reportTypeWeightRecursive,
            BigDecimal infectiousnessWeightStandard,
            BigDecimal infectiousnessWeightHigh,
            List<BigDecimal> attenuationBucketThresholdDb,
            List<BigDecimal> attenuationBucketWeights,
            int daysSinceExposureThreshold,
            double minimumWindowScore,
            int minimumDailyScore,
            Map<Integer, String> daysSinceOnsetToInfectiousness,
            String infectiousnessWhenDaysSinceOnsetMissing,
            Set<String> availableCountries,
            boolean endOfLifeReached,
            List<EndOfLifeStatistic> endOfLifeStatistics
    ) {
        this.version = version;
        this.reportTypeWeightConfirmedTest = reportTypeWeightConfirmedTest;
        this.reportTypeWeightConfirmedClinicalDiagnosis = reportTypeWeightConfirmedClinicalDiagnosis;
        this.reportTypeWeightSelfReport = reportTypeWeightSelfReport;
        this.reportTypeWeightRecursive = reportTypeWeightRecursive;
        this.infectiousnessWeightStandard = infectiousnessWeightStandard;
        this.infectiousnessWeightHigh = infectiousnessWeightHigh;
        this.attenuationBucketThresholdDb = attenuationBucketThresholdDb;
        this.attenuationBucketWeights = attenuationBucketWeights;
        this.daysSinceExposureThreshold = daysSinceExposureThreshold;
        this.minimumWindowScore = minimumWindowScore;
        this.minimumDailyScore = minimumDailyScore;
        this.daysSinceOnsetToInfectiousness = daysSinceOnsetToInfectiousness;
        this.infectiousnessWhenDaysSinceOnsetMissing = infectiousnessWhenDaysSinceOnsetMissing;
        this.availableCountries = availableCountries;
        this.endOfLifeReached = endOfLifeReached;
        this.endOfLifeStatistics = endOfLifeStatistics;
    }
}
