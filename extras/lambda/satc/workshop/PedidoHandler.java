package satc.workshop;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;

public class PedidoHandler implements RequestHandler<SQSEvent, Void> {
    private final PedidoService service;

    public PedidoHandler() {
        var config = new AwsConfig(System.getenv("AWS_ENDPOINT_URL"), System.getenv("AWS_REGION"));
        service = new PedidoService(config.dynamo(), config.sqs(), config.s3());
    }

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (var record : event.getRecords()) {
            service.processar(record.getBody());
        }
        return null;
    }
}
