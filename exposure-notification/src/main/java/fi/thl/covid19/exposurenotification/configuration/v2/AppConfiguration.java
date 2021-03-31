package fi.thl.covid19.exposurenotification.configuration.v2;

public class AppConfiguration {
    public final int version;
    public final int tokenLength;
    public final int diagnosisKeysPerSubmit;
    public final int pollingIntervalMinutes;
    public final int municipalityFetchIntervalHours;
    public final int lowRiskLimit;
    public final int highRiskLimit;

    public AppConfiguration(int version,
                            int tokenLength,
                            int diagnosisKeysPerSubmit,
                            int pollingIntervalMinutes,
                            int municipalityFetchIntervalHours,
                            int lowRiskLimit,
                            int highRiskLimit) {
        this.version = version;
        this.tokenLength = tokenLength;
        this.diagnosisKeysPerSubmit = diagnosisKeysPerSubmit;
        this.pollingIntervalMinutes = pollingIntervalMinutes;
        this.municipalityFetchIntervalHours = municipalityFetchIntervalHours;
        this.lowRiskLimit = lowRiskLimit;
        this.highRiskLimit = highRiskLimit;
    }

    public static final AppConfiguration DEFAULT = new AppConfiguration(
            1,
            12,
            14,
            60*4,
            48,
            96,
            336);
}
