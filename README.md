# Serviço de votação

API para cadastro de pautas, abertura de sessões e registro de votos. O projeto também possui uma versão assíncrona do voto, usando Kafka, e um serviço local que simula a validação de CPF.

## Como o projeto está organizado

O repositório possui dois serviços e um contrato compartilhado:

- `votacao-service`: concentra pautas, votos, resultado e integração com Kafka;
- `validacao-cpf-service`: mantém uma base de CPFs e informa se o associado está apto a votar;
- `cpf-contract`: contém os objetos compartilhados pelos dois serviços.

O fluxo da API v3 funciona assim:

```text
Cliente
  -> API de votação
  -> PostgreSQL (voto pendente + outbox)
  -> publicador da outbox
  -> Kafka
  -> consumidor
  -> serviço de CPF
  -> atualização do voto no PostgreSQL
```

A outbox foi usada para não depender de uma gravação no banco e uma publicação no Kafka acontecendo perfeitamente ao mesmo tempo. Se o Kafka estiver fora do ar, a publicação continua salva e pode ser enviada depois. Como uma mensagem pode ser entregue mais de uma vez, o consumidor também verifica se o voto ainda está pendente antes de processá-lo.

## Tecnologias

- Java 26
- Spring Boot
- Spring Data JPA
- PostgreSQL
- Apache Kafka em modo KRaft
- Docker Compose
- Springdoc OpenAPI/Swagger
- JUnit, Mockito, Embedded Kafka e Testcontainers
- k6 para teste de carga

## Executando com Docker

É necessário ter Docker e Docker Compose instalados.

Crie o arquivo local de configuração a partir do exemplo:

```powershell
Copy-Item .env.example .env
```

No Linux ou macOS:

```bash
cp .env.example .env
```

Preencha pelo menos estas variáveis no `.env`:

```dotenv
POSTGRES_DB=votacao
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
```

As demais variáveis já possuem valores sugeridos no arquivo de exemplo. Depois, suba o ambiente:

```bash
docker compose up --build
```

O ambiente possui os seguintes acessos:

| Serviço | Endereço |
|---|---|
| API de votação | `http://localhost:8080` |
| Swagger da votação | `http://localhost:8080/swagger-ui/index.html` |
| Serviço de CPF | `http://localhost:8081` |
| Swagger do CPF | `http://localhost:8081/swagger-ui/index.html` |
| PostgreSQL | `localhost:5432` |
| Kafka | `localhost:9092` |

Na primeira execução, o serviço de CPF prepara os registros do seed. Antes de testar, vale conferir se todos os containers estão prontos:

```bash
docker compose ps
```

E validar um CPF conhecido:

```bash
curl http://localhost:8081/api/v1/cpf/03425110250
```

## Versões da API de voto

A API foi mantida em três versões para mostrar a evolução do fluxo:

- `v1`: gravação básica do voto;
- `v2`: validação síncrona do CPF antes da gravação;
- `v3`: recebe e persiste o voto rapidamente, deixando a validação para o processamento assíncrono.

Na v3, receber `201 Created` significa que o voto foi aceito e salvo como `PENDENTE`. O resultado da validação deve ser consultado depois pelo endereço enviado no header `Location`.

Os possíveis estados são:

- `PENDENTE`
- `CPF_INVALIDO`
- `CPF_NAO_ENCONTRADO`
- `NAO_APTO`
- `CONTABILIZADO`
- `ERRO_VALIDACAO_CPF`
- `SERVICO_CPF_INDISPONIVEL`
- `ERRO_PROCESSAMENTO`

## Endpoints principais

### Pautas

| Método | Endpoint | Finalidade |
|---|---|---|
| `POST` | `/api/v1/pautas` | Cadastrar uma pauta |
| `GET` | `/api/v1/pautas` | Listar pautas |
| `GET` | `/api/v1/pautas/{id}` | Consultar uma pauta |
| `PUT` | `/api/v1/pautas/{id}` | Atualizar uma pauta |
| `DELETE` | `/api/v1/pautas/{id}` | Inativar uma pauta |
| `POST` | `/api/v1/pautas/{id}/open` | Abrir a sessão de votação |
| `POST` | `/api/v1/pautas/{id}/close` | Fechar e contabilizar o resultado |

Se `dataEncerramento` não for informada na abertura, a sessão fica aberta por um minuto.

Ao fechar uma pauta, votos ainda pendentes impedem a operação. Somente votos com status `CONTABILIZADO` entram em `votosSim` e `votosNao`. Em caso de empate, a pauta fica como `REPROVADO`.

### Votos

| Método | Endpoint | Finalidade |
|---|---|---|
| `POST` | `/api/v1/votos` | Registrar voto sem validação externa |
| `GET` | `/api/v1/votos/{pautaId}` | Listar votos de uma pauta |
| `POST` | `/api/v2/votos` | Registrar voto com validação síncrona |
| `POST` | `/api/v3/votos` | Receber voto para processamento assíncrono |
| `GET` | `/api/v3/votos/{id}` | Consultar o processamento de um voto |

A listagem da v1 aceita paginação e os filtros opcionais `status_processamento` e `opcao`:

```text
GET /api/v1/votos/{pautaId}?status_processamento=CONTABILIZADO&opcao=SIM
```

## Retry e DLT

Falhas temporárias de conexão com o serviço de CPF, além dos retornos `502`, `503` e `504`, passam pelo retry do Kafka. A quantidade configurada representa o total de tentativas, incluindo a primeira.

Erros definitivos, como CPF inválido ou não encontrado, não são repetidos. Quando as tentativas acabam, a mensagem segue para a DLT e o voto recebe `SERVICO_CPF_INDISPONIVEL` ou `ERRO_PROCESSAMENTO`.

Algumas linhas úteis nos logs:

```text
metric=voto_retry ...
metric=voto_dlt ...
metric=votos_kafka publicados=... falhas=... outbox_pendentes=...
```

`publicados` indica mensagens confirmadas pelo Kafka e removidas da outbox. Isso não significa, necessariamente, que todos os votos já foram validados pelo consumidor.

## Testes automatizados

Para executar os testes do serviço de votação no Windows:

```powershell
cd services/votacao-service
.\gradlew.bat test
```

No Linux ou macOS:

```bash
cd services/votacao-service
./gradlew test
```

Para o serviço de CPF, use o mesmo comando dentro de `services/validacao-cpf-service`.

Os testes cobrem regras HTTP, ciclo da pauta, voto concorrente, outbox, Kafka, retry, DLT e validação de CPF. Também há testes com PostgreSQL real usando Testcontainers, portanto o Docker deve estar disponível para essa parte da suíte.

## Teste de carga

Com a aplicação já iniciada, execute:

```bash
k6 run -e VUS=100 -e DURATION=1m performance/votos-v3.js
```

O cenário mantém a quantidade de usuários virtuais constante e envia novas requisições continuamente. O throughput é o resultado do teste, não um valor fixo configurado previamente.

Por padrão, um em cada 20 CPFs é válido, mas não está cadastrado. Os demais usam a faixa preparada pelo seed. Se a quantidade do seed for alterada, mantenha os dois lados com o mesmo valor:

```bash
k6 run \
  -e VUS=100 \
  -e DURATION=1m \
  -e CPFS_SEED=100000 \
  -e CPF_NAO_ENCONTRADO_A_CADA=20 \
  performance/votos-v3.js
```

O k6 mede quanto a API consegue receber e persistir como pendente. Como o restante do fluxo é assíncrono, acompanhe também a outbox e consulte os votos por `status_processamento` depois da carga.

## Persistência dos dados

O PostgreSQL usa o volume `postgres_data`. Parar os containers não apaga pautas ou votos:

```bash
docker compose down
```

Para remover também os dados locais e começar do zero:

```bash
docker compose down -v
```

O Kafka está configurado com um único nó para simplificar a execução local. Essa configuração não representa uma topologia de alta disponibilidade para produção.
