package satc.workshop;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "workshop.consumidor.ativo", havingValue = "true", matchIfMissing = true)
public class PedidoConsumer {
    private final PedidoService service;

    @SqsListener(value = "pedidos", maxConcurrentMessages = "1", maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "10", messageVisibilitySeconds = "30")
    public void consumir(String pedidoId) {
        try {
            service.processar(pedidoId);
        } catch (RuntimeException e) {
            log.error("Falha no pedido {}; mensagem será tentada novamente", pedidoId, e);
            throw e;
        }
    }
}
