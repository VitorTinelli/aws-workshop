package satc.workshop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "workshop.consumidor.ativo", havingValue = "true", matchIfMissing = true)
public class PedidoConsumer {
    private static final Logger log = LoggerFactory.getLogger(PedidoConsumer.class);
    private final PedidoService service;
    private final SqsClient sqs;

    public PedidoConsumer(PedidoService service, SqsClient sqs) {
        this.service = service;
        this.sqs = sqs;
    }

    @Scheduled(fixedDelayString = "${workshop.consumidor.intervalo:2000}")
    public void consumir() {
        String url = service.filaUrl();
        var mensagens = sqs.receiveMessage(r -> r.queueUrl(url).maxNumberOfMessages(1)
                .waitTimeSeconds(1).visibilityTimeout(30)).messages();
        for (var mensagem : mensagens) {
            try {
                service.processar(mensagem.body());
                sqs.deleteMessage(r -> r.queueUrl(url).receiptHandle(mensagem.receiptHandle()));
            } catch (RuntimeException e) {
                log.error("Falha no pedido {}; mensagem será tentada novamente", mensagem.body(), e);
            }
        }
    }
}
