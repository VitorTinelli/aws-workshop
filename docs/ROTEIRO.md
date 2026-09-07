# Roteiro do professor · 120 minutos

## Antes da aula (instalação, sem código pronto)

Pedir JDK 21, IDE e Docker Desktop em modo Linux. Conferir `java -version` e `docker version`. Cada aluno prepara sua conta/token LocalStack e baixa `localstack/localstack:2026.08.0`. A IDE deve usar Java 21 também para executar Gradle. Baixar previamente a distribuição Gradle e dependências usando um projeto descartável evita depender do Wi-Fi durante a aula.

Entregar apenas `index.html`. O professor mantém a referência para recuperação; não pedir que a turma clone o backend. Abrir dois terminais: um para Spring e outro para CLI. Os comandos abaixo são PowerShell; no macOS/Linux trocar `./gradlew.bat` por `./gradlew`.

## 0–10 · Initializr e primeiro endpoint

Em https://start.spring.io selecionar Gradle / Groovy, Java, Spring Boot 4.1.1, group `satc`, artifact `workshop`, package `satc.workshop`, Jar, Java 21 e somente Spring Web. Se essa versão não estiver no seletor, ajustar o plugin no arquivo gerado para coincidir com a referência antes do ensaio. Extrair e abrir. Executar `./gradlew.bat bootRun`.

Explicar o fluxo com um pedido de caderno. HTTP 202 significa aceito, não concluído. Criar `Pedido.java` com os dois records da referência. Não há banco relacional nem JPA.

## 10–25 · Infraestrutura e clientes

Digitar as quatro dependências do SDK no bloco `dependencies`:

```groovy
implementation platform('software.amazon.awssdk:bom:2.41.31')
implementation 'software.amazon.awssdk:dynamodb'
implementation 'software.amazon.awssdk:sqs'
implementation 'software.amazon.awssdk:s3'
```

Digitar o `compose.yaml` da referência e criar `.env` com o token pessoal. Explicar: o container emula APIs AWS; o SDK usa HTTP para conversar com ele. Criar os recursos, explicitamente, com os quatro comandos Docker do README. Não usar scripts automáticos de provisionamento na aula.

Digitar as propriedades e `AwsConfig.java`. Introduzir construtor, `@Configuration`, `@Bean` e os três clientes. `forcePathStyle(true)` faz o S3 usar o bucket no caminho da URL. O endereço é `http://localhost:4566`, a região `us-east-1`, as credenciais fictícias `test`.

Checkpoint: `docker compose exec localstack awslocal s3 ls` mostra o bucket.

## 25–45 · Criar e consultar no DynamoDB

Criar `PedidoService` com os clientes no construtor. Digitar `texto`, `chave`, `criar` e `buscar` da referência, **ainda sem a linha `sqs.sendMessage`**. Explicar o mapa de atributos, chave `id`, UUID e leitura consistente.

```java
private static AttributeValue texto(String valor) {
    return AttributeValue.builder().s(valor).build();
}
```

Criar `PedidoController` com apenas POST e GET. Adicionar download somente na etapa posterior. Conferir imports pela IDE.

```powershell
$body = @{cliente='Ana'; produto='Caderno'; quantidade=2} | ConvertTo-Json
$pedido = Invoke-RestMethod http://localhost:8080/pedidos -Method Post -ContentType 'application/json' -Body $body
Invoke-RestMethod "http://localhost:8080/pedidos/$($pedido.id)"
```

Checkpoint: pedido retornado como `PENDENTE`. Quantidade zero retorna 400. ID inexistente retorna 404. Os pedidos desta etapa ainda não estão na fila; criar outro na próxima etapa.

## 45–60 · Publicar na SQS

Adicionar `filaUrl` e, logo após `putItem`, a publicação:

```java
sqs.sendMessage(r -> r.queueUrl(filaUrl()).messageBody(id));
```

Criar outro pedido. Ver a mensagem antes de existir consumidor:

```powershell
$fila = docker compose exec -T localstack awslocal sqs get-queue-url --queue-name pedidos --query QueueUrl --output text
docker compose exec localstack awslocal sqs receive-message --queue-url $fila --visibility-timeout 0
```

O corpo é apenas o ID. Receber não exclui; visibilidade zero torna a mensagem imediatamente disponível novamente. Explicar a janela de falha entre gravar no banco e publicar na fila, sem implementar outbox.

## 60–85 · Processar e salvar no S3

Digitar `processar` da referência em duas partes: buscar e gerar texto; enviar arquivo ao S3. Em seguida adicionar a atualização do DynamoDB. Usar `pedidos/{id}.txt` evita criar comprovantes duplicados numa nova tentativa.

```java
String comprovante = "COMPROVANTE DO PEDIDO\nID: %s\nCliente: %s\nProduto: %s\nQuantidade: %d\n"
        .formatted(id, pedido.cliente(), pedido.produto(), pedido.quantidade());
```

Criar `PedidoConsumer` com `@Component`, `@EnableScheduling`, condição de ativação e construtor. Digitar `consumir` da referência: receber uma mensagem, processar, excluir. O `try/catch` envolve processamento e exclusão. Se ocorrer erro, registrar e deixar a mensagem voltar após 30 segundos. Não excluir em `finally`.

Checkpoint: o consumidor processa o pedido deixado na fila. Inspecionar:

```powershell
docker compose exec localstack awslocal s3 ls s3://comprovantes/pedidos/
```

## 85–100 · Status e download

Revisar a ordem S3 → DynamoDB → exclusão SQS. Digitar `comprovante` no serviço e o endpoint correspondente no controller. HTTP 409 indica que ainda está pendente. `Content-Disposition` pede ao navegador para baixar o arquivo.

```powershell
Invoke-WebRequest "http://localhost:8080/pedidos/$($pedido.id)/comprovante" -OutFile comprovante.txt
Get-Content comprovante.txt
```

Use o ID do pedido criado depois de adicionar a publicação SQS.

## 100–110 · Página pronta

Copiar `index.html` para `src/main/resources/static/index.html`. Reiniciar e abrir http://localhost:8080. Criar um pedido com nome acentuado e baixar o comprovante. A página consulta a cada dois segundos por até um minuto; falha de rede ou espera excessiva oferece nova consulta.

## 110–120 · Revisão e margem

Perguntar: quem guarda dados? Quem desacopla o processamento? Quem guarda arquivos? Por que não apagar a mensagem antes? Se houver tempo, mostrar o cenário de consumidor desligado em VALIDACAO.md. A extensão Lambda fica para depois.

O cronograma é uma estimativa, não um ensaio medido. Antes do evento, executar com digitação e explicação em voz alta. Registrar tempos em VALIDACAO.md; se ultrapassar, reduzir explicações opcionais e inspeções CLI, preservando o fluxo obrigatório. Não apresentar o tempo como garantido sem esse ensaio.

## Recuperação

Erros de conexão: conferir Docker, `docker compose ps` e endpoint. Recurso inexistente: repetir apenas o comando de criação faltante. Java incompatível: ajustar `JAVA_HOME` e JVM Gradle da IDE. Pedido eternamente pendente: olhar log do consumidor e fila; pedido criado antes da publicação precisa ser recriado. Usar os arquivos Java de referência para comparar a etapa que falhou.
