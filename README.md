# Workshop · Pedidos assíncronos

Referência para uma aula de duas horas: Java 21, Spring Boot, Spring Cloud AWS, Lombok, DynamoDB, SQS e S3 no LocalStack. Os alunos começam no Spring Initializr; somente `src/main/resources/static/index.html` é distribuído pronto.

- [Roteiro da aula](docs/ROTEIRO.md)

## Executar a referência

Use JDK 21 no terminal e na IDE e Docker Desktop com containers Linux. Esta referência usa LocalStack 3.4.0, sem token de autenticação.

```powershell
docker compose up -d localstack --wait
docker compose up -d --build app
```

Abra http://localhost:8080. O build da imagem executa os testes antes de gerar o JAR; se algum teste falhar, a imagem da aplicação não é criada. Ao iniciar, a aplicação cria a fila SQS, a tabela DynamoDB e o bucket S3 quando eles ainda não existem. O Compose não habilita persistência: não dependa dos dados após recriar o container.

## Verificação completa após o envio

Execute em PowerShell, na raiz do projeto.

```powershell
# Testes Java isolados dentro de uma imagem Docker
docker build --target test -t workshop-pedidos:test .

# LocalStack; aguarde o healthcheck antes de iniciar a aplicação
docker compose up -d localstack --wait

# Aplicação Spring Boot em container
docker compose up -d --build app
docker compose ps
docker compose logs app --tail 100

# Envio HTTP do pedido
$body = @{ cliente='Ana'; produto='Caderno'; quantidade=2 } | ConvertTo-Json
$pedido = Invoke-RestMethod http://localhost:8080/pedidos -Method Post -ContentType 'application/json' -Body $body
$pedido | Format-List
$id = $pedido.id

# Aguarde o @SqsListener e consulte até concluir
do {
    Start-Sleep -Seconds 1
    $resultado = Invoke-RestMethod "http://localhost:8080/pedidos/$id"
    $resultado | Format-List
} while ($resultado.status -ne 'CONCLUIDO')

# Confirme o registro diretamente no DynamoDB
docker compose exec localstack awslocal dynamodb get-item --table-name pedidos --key "{`"id`":{`"S`":`"$id`"}}" --consistent-read

# Confirme e baixe o comprovante diretamente do S3
docker compose exec localstack awslocal s3 ls "s3://comprovantes/pedidos/$id.txt"
docker compose exec localstack awslocal s3 cp "s3://comprovantes/pedidos/$id.txt" -
Invoke-WebRequest "http://localhost:8080/pedidos/$id/comprovante" -OutFile comprovante.txt
Get-Content comprovante.txt

# A fila deve ficar sem mensagens disponíveis ou em processamento
$fila = docker compose exec -T localstack awslocal sqs get-queue-url --queue-name pedidos --query QueueUrl --output text
$fila = $fila.Trim()
docker compose exec localstack awslocal sqs get-queue-attributes --queue-url $fila --attribute-names ApproximateNumberOfMessages ApproximateNumberOfMessagesNotVisible

# Logs do listener e encerramento do ambiente
docker compose logs app --tail 200
docker compose down
```

```mermaid
sequenceDiagram
    participant W as Página
    participant J as Spring Boot
    participant D as DynamoDB
    participant Q as SQS
    participant C as @SqsListener
    participant S as S3
    W->>J: POST /pedidos
    J->>D: Salvar PENDENTE
    J->>Q: Enviar ID
    J-->>W: 202 + pedido
    C->>Q: Receber mensagem
    C->>D: Buscar pedido
    C->>S: Salvar comprovante
    C->>D: Atualizar CONCLUIDO
    C->>Q: Excluir mensagem
    W->>J: Consultar e baixar
```

## Limites didáticos

Não é um sistema de pagamentos. Não há autenticação, DLQ ou transação entre DynamoDB e SQS. Se a publicação na fila falhar após a gravação, o pedido fica pendente e a requisição falha; o professor pode recuperar reenviando seu ID pela CLI. Em produção, discutir outbox e reconciliação. Mensagens inválidas voltam após a visibilidade de 30 segundos; uma DLQ seria o próximo passo. O arquivo tem chave e conteúdo determinísticos para tolerar entregas repetidas.

Clientes AWS usam endpoint explícito, região `us-east-1` e credenciais fictícias `test`. O material é exclusivamente local.

Referências: [SDK Java com Gradle](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/setup-project-gradle.html).
