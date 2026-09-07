package satc.workshop;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
public class AwsConfig {
    private final URI endpoint;
    private final Region region;
    private final StaticCredentialsProvider credentials =
            StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));

    public AwsConfig(@Value("${aws.endpoint}") String endpoint,
                     @Value("${aws.region}") String region) {
        this.endpoint = URI.create(endpoint);
        this.region = Region.of(region);
    }

    @Bean
    public DynamoDbClient dynamo() {
        return DynamoDbClient.builder().endpointOverride(endpoint)
                .region(region).credentialsProvider(credentials).build();
    }

    @Bean
    public SqsClient sqs() {
        return SqsClient.builder().endpointOverride(endpoint)
                .region(region).credentialsProvider(credentials).build();
    }

    @Bean
    public S3Client s3() {
        return S3Client.builder().endpointOverride(endpoint).forcePathStyle(true)
                .region(region).credentialsProvider(credentials).build();
    }
}
