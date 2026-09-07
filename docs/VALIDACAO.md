# Validação do professor

## Verificado nesta implementação

- Compilação Java 21 e geração do `bootJar` concluídas em container `eclipse-temurin:21-jdk`.
- Sete testes passaram: seis de comportamento e um de carregamento do contexto Spring.
- Extensão compilada com `-Plambda lambdaZip`; arquivo `build/distributions/pedidos-lambda.zip` gerado. A execução do handler no LocalStack ainda depende do teste integrado.
- JavaScript da página passou na checagem de sintaxe; as configurações Compose principal e Lambda passaram em `docker compose config --quiet`.
- A execução Gradle nativa no Windows falhou na conexão de loopback do runtime; o container contornou o problema sem alterar o Java alvo do projeto.
- Fluxo integrado e inspeção visual da página ainda não executados. Não havia token LocalStack configurado. O roteiro abaixo precisa ser executado após configurar `.env`.
- Digitação e explicação ainda não cronometradas com uma pessoa.

## Testes locais

`./gradlew.bat test` executa testes sem LocalStack. O teste de contexto desativa o consumidor; os testes do serviço usam clientes simulados.

## Ensaio integrado (LocalStack obrigatório)

Em um ambiente de aula vazio, seguir README e ROTEIRO do início. Não apagar recursos de outro trabalho. A referência não inicia nem provisiona serviços durante testes unitários.

1. Com consumidor ativo, criar um pedido pela página. Em até um minuto, conferir `CONCLUIDO`, download e conteúdo UTF-8 com os dados enviados.
2. Parar Spring com Ctrl+C. Iniciar `./gradlew.bat bootRun --args="--workshop.consumidor.ativo=false"`. Criar um pedido; salvar seu ID. GET retorna PENDENTE e download retorna 409. Após um minuto a página oferece nova consulta.
3. Com esse ID, conferir a fila usando `receive-message --visibility-timeout 0`. Reiniciar Spring com `./gradlew.bat bootRun`; consultar novamente e conferir conclusão.
4. Obter `$fila` como no roteiro. Executar `docker compose exec localstack awslocal sqs send-message --queue-url $fila --message-body ID_DO_PEDIDO`. Aguardar processamento. Listar `s3://comprovantes/pedidos/ID_DO_PEDIDO.txt`; deve haver somente um objeto nessa chave e o conteúdo deve ser igual ao anterior.
5. GET `/pedidos/inexistente` e `/pedidos/inexistente/comprovante`: ambos 404. POST com quantidade zero, cliente vazio ou produto ausente: 400.
6. Interromper a aplicação e enviar pelo formulário: a página deve exibir erro e reabilitar o botão. Restaurar e testar novamente. Conferir em tela estreita e navegação por Tab.
7. Para falha de processamento, usar um teste unitário com S3 indisponível: a mensagem não pode ser excluída e o status não pode ser concluído. Não apagar o bucket durante a aula.

## Registro do ensaio

| Marco | Previsto | Real |
|---|---:|---|
| Initializr | 10 min | A medir |
| Infraestrutura | 25 min | A medir |
| DynamoDB | 45 min | A medir |
| SQS | 60 min | A medir |
| Consumidor + S3 | 85 min | A medir |
| Download | 100 min | A medir |
| Página integrada | 110 min | A medir |
| Encerramento | 120 min | A medir |

O ensaio real inclui digitação, explicação e dúvidas. Executar código automaticamente não mede esse tempo.
