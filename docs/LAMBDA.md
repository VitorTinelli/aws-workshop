# Extensão · Substituir o consumidor por Lambda Java

Fora das duas horas. O handler reutiliza `PedidoService.processar` e cria os clientes sem iniciar o Spring. As bibliotecas Spring ficam no pacote porque o serviço usa suas anotações e exceções; a extensão prioriza reutilização, não otimização do pacote.

## Preparar

Docker Desktop deve executar containers Linux. Baixar previamente `public.ecr.aws/lambda/java:21`. A execução Lambda usa o socket Docker e uma rede compartilhada. Subir esta configuração **antes de criar os recursos do ensaio da extensão**, pois recriar LocalStack pode perder o estado anterior:

```powershell
docker compose -f compose.yaml -f extras/compose.lambda.yaml up -d --wait
```

Criar tabela, fila e bucket com os comandos do README, usando os mesmos dois argumentos `-f` nos comandos Compose. O gateway continua em localhost:4566 para o Spring. A Lambda usa `http://localstack:4566`; localhost dentro dela aponta para o próprio container.

Em um projeto criado pelos alunos, adicionar ao fim de `build.gradle`:

```groovy
apply from: 'extras/lambda.gradle'
```

Estudar e digitar `extras/lambda/satc/workshop/PedidoHandler.java`. `SQSEvent` contém mensagens; o corpo continua sendo o ID. Não capturar silenciosamente erros: a integração exclui a mensagem quando o handler termina com sucesso e tenta novamente quando ele falha. Usaremos lote de tamanho um.

```powershell
./gradlew.bat -Plambda lambdaZip
docker compose -f compose.yaml -f extras/compose.lambda.yaml cp build/distributions/pedidos-lambda.zip localstack:/tmp/pedidos-lambda.zip
docker compose -f compose.yaml -f extras/compose.lambda.yaml cp extras/trust-policy.json localstack:/tmp/trust-policy.json
docker compose -f compose.yaml -f extras/compose.lambda.yaml cp extras/lambda-environment.json localstack:/tmp/lambda-environment.json
docker compose -f compose.yaml -f extras/compose.lambda.yaml exec localstack awslocal iam create-role --role-name pedidos-lambda --assume-role-policy-document file:///tmp/trust-policy.json
docker compose -f compose.yaml -f extras/compose.lambda.yaml exec localstack awslocal iam attach-role-policy --role-name pedidos-lambda --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaSQSQueueExecutionRole
docker compose -f compose.yaml -f extras/compose.lambda.yaml exec localstack awslocal lambda create-function --function-name pedidos --runtime java21 --handler satc.workshop.PedidoHandler::handleRequest --role arn:aws:iam::000000000000:role/pedidos-lambda --zip-file fileb:///tmp/pedidos-lambda.zip --timeout 10 --memory-size 512 --environment file:///tmp/lambda-environment.json
docker compose -f compose.yaml -f extras/compose.lambda.yaml exec localstack awslocal lambda wait function-active-v2 --function-name pedidos
```

O material usa a emulação local padrão sem enforcement IAM. Uma publicação real exigiria permissões limitadas de DynamoDB e S3 para a função e configuração própria; não executar estes comandos na AWS real.

Na referência, o carregamento já é condicional à opção `-Plambda`; não adicionar outro `apply`. Copiar também `extras/lambda.gradle` e os JSONs para o projeto dos alunos ao iniciar a extensão.

## Trocar o consumidor

Parar Spring e iniciar com o consumidor desativado **antes** de criar o vínculo:

```powershell
./gradlew.bat bootRun --args="--workshop.consumidor.ativo=false"
```

Em outro terminal:

```powershell
$fila = docker compose -f compose.yaml -f extras/compose.lambda.yaml exec -T localstack awslocal sqs get-queue-url --queue-name pedidos --query QueueUrl --output text
docker compose -f compose.yaml -f extras/compose.lambda.yaml exec localstack awslocal sqs set-queue-attributes --queue-url $fila --attributes VisibilityTimeout=60
docker compose -f compose.yaml -f extras/compose.lambda.yaml exec localstack awslocal lambda create-event-source-mapping --function-name pedidos --event-source-arn arn:aws:sqs:us-east-1:000000000000:pedidos --batch-size 1
```

Criar pedido pela página e verificar status, download e logs em `/aws/lambda/pedidos`. Reenviar o ID e conferir a mesma chave S3. Para voltar ao consumidor Spring, listar os vínculos com `awslocal lambda list-event-source-mappings --function-name pedidos`, desativar o UUID retornado com `awslocal lambda update-event-source-mapping --uuid UUID --no-enabled`, aguardar estado Disabled e só então reiniciar Spring com consumidor ativo.

Referência: [Lambda no LocalStack](https://docs.localstack.cloud/aws/services/lambda/).
