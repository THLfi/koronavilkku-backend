package fi.thl.covid19.publishtoken;

import fi.thl.covid19.publishtoken.sms.SmsConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties({ SmsConfig.class })
@SpringBootApplication
public class PublishTokenApplication {

	public static void main(String[] args) {
		SpringApplication.run(PublishTokenApplication.class, args);
	}
}
