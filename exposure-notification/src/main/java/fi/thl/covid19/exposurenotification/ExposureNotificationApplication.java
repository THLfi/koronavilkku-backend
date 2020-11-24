package fi.thl.covid19.exposurenotification;

import fi.thl.covid19.exposurenotification.batch.SignatureConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@EnableScheduling
@EnableAsync
@EnableConfigurationProperties({SignatureConfig.class, FederationGatewayRestClientProperties.class})
@SpringBootApplication
public class ExposureNotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExposureNotificationApplication.class, args);
    }

    @Bean(name = "callbackAsyncExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("callback-processor-");
        executor.initialize();
        return executor;
    }
}
