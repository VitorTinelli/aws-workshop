# Imagem da arquitetura

Arquivo: `arquitetura-workshop.png`. Gerado com a ferramenta integrada image_gen e revisado para conferir os serviços e a ordem de processamento.

Prompt final: diagrama em português da arquitetura local, com navegador, aplicação Java 21/Spring Boot contendo API REST e consumidor @Scheduled, e DynamoDB, SQS e S3 emulados no Docker/LocalStack. Conectar a aplicação a cada serviço com uma linha bidirecional identificada. Mostrar separadamente a sequência: gravar PENDENTE no DynamoDB, publicar ID na SQS, receber mensagem, buscar pedido no DynamoDB, salvar comprovante no S3, atualizar CONCLUIDO no DynamoDB e excluir mensagem na SQS. Mostrar Lambda Java em painel opcional, substituindo o consumidor Spring. Consultas e downloads passam pela API. Arquivo no S3: pedidos/{id}.txt. Estilo claro, legível e adequado à projeção em aula.
