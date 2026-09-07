package satc.workshop;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

@Service
public class PedidoService {
    private final DynamoDbClient dynamo;
    private final SqsClient sqs;
    private final S3Client s3;

    public PedidoService(DynamoDbClient dynamo, SqsClient sqs, S3Client s3) {
        this.dynamo = dynamo;
        this.sqs = sqs;
        this.s3 = s3;
    }

    private static AttributeValue texto(String valor) {
        return AttributeValue.builder().s(valor).build();
    }

    private static Map<String, AttributeValue> chave(String id) {
        return Map.of("id", texto(id));
    }

    public String filaUrl() {
        return sqs.getQueueUrl(r -> r.queueName("pedidos")).queueUrl();
    }

    public Pedido criar(Pedido.Entrada entrada) {
        if (entrada.cliente() == null || entrada.cliente().isBlank()
                || entrada.produto() == null || entrada.produto().isBlank()
                || entrada.quantidade() == null || entrada.quantidade() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe cliente, produto e quantidade positiva");
        }
        String id = UUID.randomUUID().toString();
        Pedido pedido = new Pedido(id, entrada.cliente().trim(), entrada.produto().trim(), entrada.quantidade(), "PENDENTE");
        dynamo.putItem(r -> r.tableName("pedidos").item(Map.of(
                "id", texto(id), "cliente", texto(pedido.cliente()),
                "produto", texto(pedido.produto()), "status", texto(pedido.status()),
                "quantidade", AttributeValue.builder().n(Integer.toString(pedido.quantidade())).build())));
        sqs.sendMessage(r -> r.queueUrl(filaUrl()).messageBody(id));
        return pedido;
    }

    public Pedido buscar(String id) {
        var item = dynamo.getItem(r -> r.tableName("pedidos").key(chave(id)).consistentRead(true)).item();
        if (item.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado");
        }
        return new Pedido(id, item.get("cliente").s(), item.get("produto").s(),
                Integer.parseInt(item.get("quantidade").n()), item.get("status").s());
    }

    public void processar(String id) {
        Pedido pedido = buscar(id);
        String comprovante = "COMPROVANTE DO PEDIDO\nID: %s\nCliente: %s\nProduto: %s\nQuantidade: %d\n"
                .formatted(id, pedido.cliente(), pedido.produto(), pedido.quantidade());
        s3.putObject(r -> r.bucket("comprovantes").key("pedidos/" + id + ".txt")
                .contentType("text/plain; charset=utf-8"), RequestBody.fromString(comprovante));
        dynamo.updateItem(r -> r.tableName("pedidos").key(chave(id))
                .updateExpression("SET #s = :s").expressionAttributeNames(Map.of("#s", "status"))
                .expressionAttributeValues(Map.of(":s", texto("CONCLUIDO"))));
    }

    public byte[] comprovante(String id) {
        if (!buscar(id).status().equals("CONCLUIDO")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pedido ainda pendente");
        }
        return s3.getObjectAsBytes(r -> r.bucket("comprovantes").key("pedidos/" + id + ".txt")).asByteArray();
    }
}
