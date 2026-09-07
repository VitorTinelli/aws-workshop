# Roteiro · Pedidos assíncronos

## 1. Preparação

Necessário: JDK 21, Intellij e Docker Desktop. Confira:

```powershell
java -version
docker version
```

O projeto usa LocalStack 3.4.0: uma simulação local de DynamoDB, SQS e S3. Não exige conta AWS nem token.

## 2. Spring Initializr

Em [start.spring.io](https://start.spring.io), escolha:

- Gradle / Groovy, Java, Spring Boot `4.1.1`, Jar e Java `21`;
- Group `satc`, Artifact `workshop`, Package `satc.workshop`;
- Dependência: **Spring Web**.

Gere, extraia e abra na IDE. Rode uma vez com `./gradlew.bat bootRun` e depois pare. O Spring Boot já fornece o servidor HTTP em `http://localhost:8080`.

## 3. Dependências

Substitua `build.gradle` inteiro por:

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.1'
    id 'io.spring.dependency-management' version '1.1.7'
}
group = 'satc'
version = '0.0.1-SNAPSHOT'
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
repositories { mavenCentral() }
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation platform('software.amazon.awssdk:bom:2.41.31')
    implementation platform('io.awspring.cloud:spring-cloud-aws-dependencies:4.1.0')
    implementation 'io.awspring.cloud:spring-cloud-aws-starter-sqs'
    implementation 'software.amazon.awssdk:dynamodb'
    implementation 'software.amazon.awssdk:s3'
    compileOnly 'org.projectlombok:lombok:1.18.48'
    annotationProcessor 'org.projectlombok:lombok:1.18.48'
    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
    testCompileOnly 'org.projectlombok:lombok:1.18.48'
    testAnnotationProcessor 'org.projectlombok:lombok:1.18.48'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
tasks.named('test') { useJUnitPlatform() }
```

**Explique:** os BOMs mantêm versões AWS compatíveis. O starter fornece a integração do `@SqsListener`; DynamoDB e S3 são clientes usados diretamente.

## 4. LocalStack

Na raiz, crie `compose.yaml`:

```yaml
services:
  localstack:
    image: localstack/localstack:3.4.0
    ports:
      - "4566:4566"
    environment:
      AWS_DEFAULT_REGION: us-east-1
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:4566/_localstack/health"]
      interval: 5s
      timeout: 3s
      retries: 30
```

Inicie-o:

```powershell
docker compose up -d localstack --wait
docker compose ps
```

**Explique:** `4566` é o endpoint local único. Nada será enviado à AWS real.

## 5. Propriedades

Em `src/main/resources/application.properties`:

```properties
spring.application.name=workshop
aws.endpoint=${AWS_ENDPOINT_URL:http://localhost:4566}
aws.region=us-east-1
spring.cloud.aws.credentials.access-key=test
spring.cloud.aws.credentials.secret-key=test
spring.cloud.aws.region.static=${aws.region}
spring.cloud.aws.sqs.endpoint=${aws.endpoint}
spring.cloud.aws.sqs.queue-not-found-strategy=CREATE
```

**Explique:** `aws.endpoint` aponta ao LocalStack; `AWS_ENDPOINT_URL`, se definida, substitui esse padrão. As credenciais são fictícias. As propriedades `spring.cloud.aws.*` configuram o cliente SQS automático; `CREATE` cria a fila se ainda não existir.

## 6. Classe inicial

Confira `src/main/java/satc/workshop/WorkshopApplication.java`:

```java
package satc.workshop;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
public class WorkshopApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkshopApplication.class, args);
    }
}
```

`@SpringBootApplication` localiza componentes abaixo de `satc.workshop`.

## 7. Modelo

Crie o pacote `satc.workshop.pedido` e `Pedido.java`:

```java
package satc.workshop.pedido;
public record Pedido(String id, String cliente, String produto, int quantidade, String status) {
    public record Entrada(String cliente, String produto, Integer quantidade) {}
}
```

`Entrada` representa o JSON recebido; `Pedido` é a resposta, já com ID e status criados pelo servidor.

## 8. Clientes AWS

Crie o pacote `satc.workshop.aws` e `AwsConfig.java`:

```java
package satc.workshop.aws;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
@Configuration
public class AwsConfig {
    private final URI endpoint;
    private final Region region;
    private final StaticCredentialsProvider credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
    public AwsConfig(@Value("${aws.endpoint}") String endpoint, @Value("${aws.region}") String region) {
        this.endpoint = URI.create(endpoint); this.region = Region.of(region);
    }
    @Bean public DynamoDbClient dynamo() { return DynamoDbClient.builder().endpointOverride(endpoint).region(region).credentialsProvider(credentials).build(); }
    @Bean public SqsClient sqs() { return SqsClient.builder().endpointOverride(endpoint).region(region).credentialsProvider(credentials).build(); }
    @Bean public S3Client s3() { return S3Client.builder().endpointOverride(endpoint).forcePathStyle(true).region(region).credentialsProvider(credentials).build(); }
}
```

Cada `@Bean` disponibiliza um cliente para injeção. `endpointOverride` evita AWS real e `forcePathStyle(true)` torna o S3 compatível com LocalStack.

## 9. Infraestrutura automática

Crie `AwsResourceInitializer.java`:

```java
package satc.workshop.aws;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;
@Component @RequiredArgsConstructor
@ConditionalOnProperty(name = "workshop.infra.ativo", havingValue = "true", matchIfMissing = true)
public class AwsResourceInitializer implements ApplicationRunner {
    private final DynamoDbClient dynamo; private final S3Client s3;
    public void run(ApplicationArguments args) { createTableIfNotExists(); createBucketIfNotExists(); }
    private void createTableIfNotExists() {
        try { dynamo.describeTable(r -> r.tableName("pedidos")); }
        catch (ResourceNotFoundException e) { dynamo.createTable(r -> r.tableName("pedidos").attributeDefinitions(a -> a.attributeName("id").attributeType("S")).keySchema(k -> k.attributeName("id").keyType("HASH")).billingMode("PAY_PER_REQUEST")); }
    }
    private void createBucketIfNotExists() {
        try { s3.headBucket(r -> r.bucket("comprovantes")); }
        catch (S3Exception e) { if (e.statusCode() != 404) throw e; s3.createBucket(r -> r.bucket("comprovantes")); }
    }
}
```

`ApplicationRunner` executa ao iniciar. Primeiro verifica; depois cria tabela `pedidos` e bucket `comprovantes` apenas se faltarem.

## 10. Serviço

Crie `PedidoService.java`:

```java
package satc.workshop.pedido;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
@Service @RequiredArgsConstructor
public class PedidoService {
    private final DynamoDbClient dynamo; private final SqsClient sqs; private final S3Client s3;
    private static AttributeValue texto(String valor) { return AttributeValue.builder().s(valor).build(); }
    private static Map<String, AttributeValue> chave(String id) { return Map.of("id", texto(id)); }
    public String filaUrl() { return sqs.getQueueUrl(r -> r.queueName("pedidos")).queueUrl(); }
    public Pedido criar(Pedido.Entrada entrada) {
        if (entrada.cliente() == null || entrada.cliente().isBlank() || entrada.produto() == null || entrada.produto().isBlank() || entrada.quantidade() == null || entrada.quantidade() < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe cliente, produto e quantidade positiva");
        String id = UUID.randomUUID().toString();
        Pedido pedido = new Pedido(id, entrada.cliente().trim(), entrada.produto().trim(), entrada.quantidade(), "PENDENTE");
        dynamo.putItem(r -> r.tableName("pedidos").item(Map.of("id", texto(id), "cliente", texto(pedido.cliente()), "produto", texto(pedido.produto()), "status", texto(pedido.status()), "quantidade", AttributeValue.builder().n(Integer.toString(pedido.quantidade())).build())));
        sqs.sendMessage(r -> r.queueUrl(filaUrl()).messageBody(id)); return pedido;
    }
    public Pedido buscar(String id) {
        var item = dynamo.getItem(r -> r.tableName("pedidos").key(chave(id)).consistentRead(true)).item();
        if (item.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado");
        return new Pedido(id, item.get("cliente").s(), item.get("produto").s(), Integer.parseInt(item.get("quantidade").n()), item.get("status").s());
    }
    public void processar(String id) {
        Pedido p = buscar(id); String texto = "COMPROVANTE DO PEDIDO\nID: %s\nCliente: %s\nProduto: %s\nQuantidade: %d\n".formatted(id, p.cliente(), p.produto(), p.quantidade());
        s3.putObject(r -> r.bucket("comprovantes").key("pedidos/" + id + ".txt").contentType("text/plain; charset=utf-8"), RequestBody.fromString(texto));
        dynamo.updateItem(r -> r.tableName("pedidos").key(chave(id)).updateExpression("SET #s = :s").expressionAttributeNames(Map.of("#s", "status")).expressionAttributeValues(Map.of(":s", texto("CONCLUIDO"))));
    }
    public byte[] comprovante(String id) {
        if (!buscar(id).status().equals("CONCLUIDO")) throw new ResponseStatusException(HttpStatus.CONFLICT, "Pedido ainda pendente");
        return s3.getObjectAsBytes(r -> r.bucket("comprovantes").key("pedidos/" + id + ".txt")).asByteArray();
    }
}
```

**Ordem do fluxo:** valida → gera UUID → grava `PENDENTE` no DynamoDB → envia **somente o ID** para SQS. Depois, o consumidor lê o pedido, grava o arquivo S3 e marca `CONCLUIDO`.

## 11. Controller

Crie `PedidoController.java`:

```java
package satc.workshop.pedido;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/pedidos") @RequiredArgsConstructor
public class PedidoController {
    private final PedidoService service;
    @PostMapping @ResponseStatus(HttpStatus.ACCEPTED)
    public Pedido criar(@RequestBody Pedido.Entrada entrada) { return service.criar(entrada); }
    @GetMapping("/{id}") public Pedido buscar(@PathVariable String id) { return service.buscar(id); }
    @GetMapping("/{id}/comprovante")
    public ResponseEntity<byte[]> comprovante(@PathVariable String id) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=utf-8").header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"comprovante.txt\"").body(service.comprovante(id));
    }
}
```

O POST retorna **202 Accepted**: o pedido foi aceito, mas ainda será processado. O último endpoint baixa o arquivo somente após a conclusão.

## 12. Consumidor SQS

Crie `PedidoConsumer.java`:

```java
package satc.workshop.pedido;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
@Slf4j @Component @RequiredArgsConstructor
@ConditionalOnProperty(name = "workshop.consumidor.ativo", havingValue = "true", matchIfMissing = true)
public class PedidoConsumer {
    private final PedidoService service;
    @SqsListener(value = "pedidos", maxConcurrentMessages = "1", maxMessagesPerPoll = "1", pollTimeoutSeconds = "10", messageVisibilitySeconds = "30")
    public void consumir(String pedidoId) {
        try { service.processar(pedidoId); }
        catch (RuntimeException e) { log.error("Falha no pedido {}; mensagem será tentada novamente", pedidoId, e); throw e; }
    }
}
```

`@SqsListener` espera mensagens, ao contrário de `@Scheduled`, que executaria por intervalo. Os limites `1` deixam a demonstração sequencial. O long polling espera até 10 segundos; a mensagem fica invisível por 30. Sucesso remove a mensagem; erro a deixa disponível para nova tentativa.

## 13. Testar API e página

Inicie: `./gradlew.bat bootRun`. Em outro terminal:

```powershell
$body = @{ cliente='Ana'; produto='Caderno'; quantidade=2 } | ConvertTo-Json
$pedido = Invoke-RestMethod http://localhost:8080/pedidos -Method Post -ContentType 'application/json' -Body $body
$id = $pedido.id
do { Start-Sleep 1; $resultado = Invoke-RestMethod "http://localhost:8080/pedidos/$id" } while ($resultado.status -ne 'CONCLUIDO')
Invoke-WebRequest "http://localhost:8080/pedidos/$id/comprovante" -OutFile comprovante.txt
Get-Content comprovante.txt
```

## 14. Inserir o site estático

Crie a pasta `static` dentro de `src/main/resources`. A estrutura deve ficar assim:

```text
src
└── main
    └── resources
        ├── application.properties
        └── static
            └── index.html
```

Copie o `index.html` entregue para `src/main/resources/static/index.html`. Não é necessário criar controller para a página: o Spring Boot serve automaticamente arquivos dentro de `resources/static`.

No JavaScript da página, destaque estas chamadas:

```javascript
await fetch('/pedidos', { method: 'POST', ... });
await fetch('/pedidos/' + encodeURIComponent(pedidoId));
download.href = '/pedidos/' + encodeURIComponent(pedidoId) + '/comprovante';
```

Elas são URLs relativas: como a página e a API estão no mesmo servidor Spring Boot, `/pedidos` significa `http://localhost:8080/pedidos`.

Reinicie a aplicação e abra exatamente:

```text
http://localhost:8080/
```

Preencha o formulário, envie um pedido e aguarde o status `CONCLUIDO` para testar o download.

**Erro comum:** não abra `index.html` diretamente pelo explorador de arquivos, pelo Live Server da IDE ou pela porta `4566`. Nesses casos, as URLs relativas não chegam à API Spring e podem responder 404.

## Perguntas para discussão

### Onde ficam o pedido, a mensagem e o comprovante?

O pedido, com cliente, produto, quantidade e status, fica na tabela `pedidos` do DynamoDB. A fila SQS guarda temporariamente apenas o ID do pedido. O comprovante é um arquivo de texto no bucket S3 `comprovantes`, com a chave `pedidos/{id}.txt`.

### Por que responder 202, e não esperar o processamento?

O HTTP 202 significa que a API aceitou o pedido, mas o trabalho continua em segundo plano. Assim, o navegador não precisa esperar a criação do arquivo, e o consumidor pode processar no próprio ritmo.

### Por que enviar somente o ID para a fila?

O DynamoDB é a fonte dos dados do pedido. Mandar só o ID mantém a mensagem pequena e evita trabalhar com uma cópia possivelmente desatualizada dos dados. O consumidor usa o ID para buscar a versão atual antes de processar.

### O que ocorre se o consumidor falhar?

O método relança a exceção. A mensagem não é confirmada e, depois dos 30 segundos de visibilidade, o SQS pode entregá-la novamente. Neste projeto não há DLQ, então uma falha persistente gera novas tentativas indefinidamente.

### O que faltaria em produção?

Uma DLQ limita mensagens problemáticas; o padrão outbox reduz a janela entre gravar no banco e publicar na fila; autenticação protege a API; métricas, logs e alertas permitem observar falhas; persistência do ambiente impede perder dados ao recriar containers. Também seriam necessários controle de acesso AWS, validação mais completa e uma estratégia de backup.
