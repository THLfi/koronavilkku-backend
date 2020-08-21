package fi.thl.covid19.exposurenotification;

import fi.thl.covid19.exposurenotification.batch.SignatureConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties({ SignatureConfig.class })
@SpringBootApplication
public class ExposureNotificationApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExposureNotificationApplication.class, args);
	}

}
