package fi.thl.covid19.exposurenotification.diagnosiskey.v1;

import fi.thl.covid19.exposurenotification.batch.BatchId;
import fi.thl.covid19.exposurenotification.configuration.v1.AppConfiguration;
import fi.thl.covid19.exposurenotification.configuration.v1.ExposureConfiguration;
import fi.thl.covid19.exposurenotification.configuration.v2.ExposureConfigurationV2;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class Status {

    public final List<String> batches;
    public final Optional<AppConfiguration> appConfig;
    public final Optional<ExposureConfiguration> exposureConfig;
    public final Optional<ExposureConfigurationV2> exposureConfigV2;

    public Status(List<BatchId> batches,
                  Optional<AppConfiguration> appConfig,
                  Optional<ExposureConfiguration> exposureConfig,
                  Optional<ExposureConfigurationV2> exposureConfigV2) {
        this.batches = requireNonNull(batches).stream().map(BatchId::toString).collect(Collectors.toList());
        this.appConfig = requireNonNull(appConfig);
        this.exposureConfig = requireNonNull(exposureConfig);
        this.exposureConfigV2 = requireNonNull(exposureConfigV2);
    }
}
