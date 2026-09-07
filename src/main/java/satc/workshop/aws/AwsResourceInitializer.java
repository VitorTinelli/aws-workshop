package satc.workshop.aws;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "workshop.infra.ativo", havingValue = "true", matchIfMissing = true)
public class AwsResourceInitializer implements ApplicationRunner {
    private static final String TABELA_PEDIDOS = "pedidos";
    private static final String BUCKET_COMPROVANTES = "comprovantes";

    private final DynamoDbClient dynamo;
    private final S3Client s3;

    @Override
    public void run(ApplicationArguments args) {
        createTableIfNotExists();
        createBucketIfNotExists();
    }

    private void createTableIfNotExists() {
        try {
            dynamo.describeTable(r -> r.tableName(TABELA_PEDIDOS));
        } catch (ResourceNotFoundException e) {
                dynamo.createTable(r -> r.tableName(TABELA_PEDIDOS)
                        .attributeDefinitions(a -> a.attributeName("id").attributeType("S"))
                        .keySchema(k -> k.attributeName("id").keyType("HASH"))
                        .billingMode("PAY_PER_REQUEST"));
        }
    }

    private void createBucketIfNotExists() {
        try {
            s3.headBucket(r -> r.bucket(BUCKET_COMPROVANTES));
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                throw e;
            }
            s3.createBucket(r -> r.bucket(BUCKET_COMPROVANTES));
        }
    }
}
