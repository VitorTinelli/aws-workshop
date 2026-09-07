package satc.workshop;

import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.springframework.web.server.ResponseStatusException;
import satc.workshop.pedido.Pedido;
import satc.workshop.pedido.PedidoConsumer;
import satc.workshop.pedido.PedidoService;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;

class PedidoServiceTests {
    private final DynamoDbClient dynamo = mock(DynamoDbClient.class);
    private final SqsClient sqs = mock(SqsClient.class);
    private final S3Client s3 = mock(S3Client.class);
    private final PedidoService service = new PedidoService(dynamo, sqs, s3);

    private void pedidoPendente() {
        when(dynamo.getItem(any(Consumer.class))).thenReturn(GetItemResponse.builder().item(Map.of(
                "cliente", AttributeValue.fromS("Ana"), "produto", AttributeValue.fromS("Caderno"),
                "quantidade", AttributeValue.fromN("2"), "status", AttributeValue.fromS("PENDENTE"))).build());
    }

    @Test void inexistenteRetorna404() {
        when(dynamo.getItem(any(Consumer.class))).thenReturn(GetItemResponse.builder().item(Map.of()).build());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.buscar("x")).getStatusCode().value());
    }

    @Test void pendenteNaoBaixaArquivo() {
        pedidoPendente();
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.comprovante("x")).getStatusCode().value());
        verifyNoInteractions(s3);
    }

    @Test void entradaInvalidaNaoGravaNemEnfileira() {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.criar(new Pedido.Entrada("Ana", "Caderno", 0))).getStatusCode().value());
        verifyNoInteractions(dynamo, sqs, s3);
    }

    @Test void falhaS3NaoConcluiPedido() {
        pedidoPendente();
        when(s3.putObject(any(Consumer.class), any(RequestBody.class))).thenThrow(new RuntimeException("offline"));
        assertThrows(RuntimeException.class, () -> service.processar("x"));
        verify(dynamo, never()).updateItem(any(Consumer.class));
    }

    @Test void reprocessamentoMantemChaveEConteudo() throws Exception {
        pedidoPendente();
        service.processar("x"); service.processar("x");
        var requests = org.mockito.ArgumentCaptor.forClass(Consumer.class);
        var bodies = org.mockito.ArgumentCaptor.forClass(RequestBody.class);
        verify(s3, times(2)).putObject(requests.capture(), bodies.capture());
        for (var configure : requests.getAllValues()) {
            var builder = PutObjectRequest.builder(); configure.accept(builder);
            assertEquals("pedidos/x.txt", builder.build().key());
        }
        try (var first = bodies.getAllValues().get(0).contentStreamProvider().newStream();
             var second = bodies.getAllValues().get(1).contentStreamProvider().newStream()) {
            assertArrayEquals(first.readAllBytes(), second.readAllBytes());
        }
    }

    @Test void consumidorPropagaFalhaParaSqsTentarNovamente() {
        PedidoService processamento = mock(PedidoService.class);
        var consumer = new PedidoConsumer(processamento);
        doThrow(new RuntimeException("offline")).when(processamento).processar("x");
        assertThrows(RuntimeException.class, () -> consumer.consumir("x"));
        verify(processamento).processar("x");
    }
}
