# Sprint 7 - Kafka в проде + интеграция с Big Data экосистемой

# Задание 1. Развёртывание и настройка Kafka-кластера в AWS

## 1. Создан кластер MSK
- kafka.m5.large
- Количество брокеров: 3 (по 1 в каждой AZ)
- Аутентификация: SASL/SCRAM + TLS (порт 9096)
- Более подробная информация на скринах в папке de.ya.kafka.screens.config.aws.msk

## 2. Создан EC2-инстанс для взаимодействия с кластером
- r7i.large в той же VPC и регионе, что и MSK
- Более подробная информация на скринах в папке de.ya.kafka.screens.config.aws.ec2

## 3. Создание топика для работы с ConnectionRegistry

```bash
BROKERS="\
b-1.mskyakafkatask7.98yru0.c4.kafka.us-east-1.amazonaws.com:9096,\
b-2.mskyakafkatask7.98yru0.c4.kafka.us-east-1.amazonaws.com:9096,\
b-3.mskyakafkatask7.98yru0.c4.kafka.us-east-1.amazonaws.com:9096"

kafka-topics.sh --create   
    --bootstrap-server "$BROKERS"   
    --command-config ~/kafka-conf/client.properties   
    --topic ya-kafka-task-7   
    --partitions 3   
    --replication-factor 3   
    --config retention.ms=604800000   
    --config segment.bytes=1073741824
    --config cleanup.policy=compact,delete
```

## 4. Поднимаем Docker контейнер со Schema Registry и добавлем схему
```bash
BOOTSTRAP="\
b-1.mskyakafkatask7.98yru0.c4.kafka.us-east-1.amazonaws.com:9096,\
b-2.mskyakafkatask7.98yru0.c4.kafka.us-east-1.amazonaws.com:9096,\
b-3.mskyakafkatask7.98yru0.c4.kafka.us-east-1.amazonaws.com:9096"


docker run -d \
    --name schema-registry \
    -p 8081:8081 \
    -v /home/ubuntu/projects/security:/security \
    -e SCHEMA_REGISTRY_HOST_NAME=localhost \
    -e SCHEMA_REGISTRY_LISTENERS=http://0.0.0.0:8081 \
    -e SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS=$BOOTSTRAP \
    -e SCHEMA_REGISTRY_KAFKASTORE_SECURITY_PROTOCOL=SASL_SSL \
    -e SCHEMA_REGISTRY_KAFKASTORE_SASL_MECHANISM=SCRAM-SHA-512 \
    -e SCHEMA_REGISTRY_KAFKASTORE_SASL_JAAS_CONFIG='org.apache.kafka.common.security.scram.ScramLoginModule required username="ya-kafka-task-7" password="123MySuperSecret";' \
    -e SCHEMA_REGISTRY_KAFKASTORE_SSL_TRUSTSTORE_LOCATION=/security/truststore.jks \
    -e SCHEMA_REGISTRY_KAFKASTORE_SSL_TRUSTSTORE_PASSWORD=changeit \
    confluentinc/cp-schema-registry:7.6.1
  
SCHEMA=$(jq -c . schema.json | jq -Rs .)

curl -s -X POST http://localhost:8081/subjects/topic-1-value/versions \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  -d "{
    \"schemaType\": \"JSON\",
    \"schema\": $SCHEMA
  }"
  
```

## 5. Проверка продюсера и консюмера, работающих с MSK и Schema Registry
- запускаем `ProducerApp.java` - результат de/ya/kafka/screens/task1/produced_messages_to_aws.png
- запускаем `ConsumerApp.java` - результат de/ya/kafka/screens/task1/consumed_messages_on_aws.png

---

# Задание 2. Интеграция Kafka ↔ Apache NiFi

## 1. Запуск NiFi (Docker на EC2)
```bash
docker run -d \
  --name nifi \
  -p 8080:8080 \
  -e NIFI_WEB_HTTP_PORT=8080 \
  -e NIFI_JVM_HEAP_INIT=1g \
  -e NIFI_JVM_HEAP_MAX=2g \
  --restart unless-stopped \
  -v $(pwd)/security/truststore.jks:/opt/nifi/nifi-current/conf/truststore.jks:ro \
  -v $(pwd)/nifi_state:/opt/nifi/nifi-current/state \
  -v $(pwd)/nifi_database_repository:/opt/nifi/nifi-current/database_repository \
  -v $(pwd)/nifi_flowfile_repository:/opt/nifi/nifi-current/flowfile_repository \
  -v $(pwd)/nifi_content_repository:/opt/nifi/nifi-current/content_repository \
  -v $(pwd)/nifi_provenance_repository:/opt/nifi/nifi-current/provenance_repository \
  apache/nifi:1.26.0

```

## 2. Настройка NiFi для публикации в MSK
- настраиваем ssh туннель к EC2 и заходи м в UI NiFi (http://localhost:8080/nifi)
```bash
ssh -i ~/.ssh/ya-kafka-task-7.pem -L 8081:localhost:8081 -L 8080:localhost:8080 ubuntu@ec2-13-221-115-14.compute-1.amazonaws.com
```
- Создаём StandardSSLContextService (`de/ya/kafka/screens/task2/nifi_ssl-context_settings.png`
- Cоздаём Processor GeneerateFlowFile (`de/ya/kafka/screens/task2/nifi_generate-flow_settings.png`)
- Cоздаём Processor PublishKafka (`de/ya/kafka/screens/task2/nifi_publish-kafka_settings_1(2).png`)
- Cоздаём топик nifi-test в MSK:
```bash
kafka-topics.sh --create \
  --bootstrap-server "$BROKERS" \
  --command-config ~/kafka-conf/client.properties \
  --topic nifi-test \
  --partitions 3 \
  --replication-factor 3 \
  --config retention.ms=604800000 \
  --config segment.bytes=1073741824 \
  --config cleanup.policy=compact,delete

```

## 3. Проверка интеграции (топика nifi-test в MSK)
```bash
kafka-console-consumer.sh \
  --bootstrap-server "$BOOTSTRAP" \
  --consumer.config /home/ubuntu/projects/security/client.properties \
  --topic nifi-test \
  --from-beginning
  
  
{"source":"nifi","ts":"1760838852584","msg":"hello from nifi"}
{"source":"nifi","ts":"1760838912586","msg":"hello from nifi"}
{"source":"nifi","ts":"1760839212596","msg":"hello from nifi"}
{"source":"nifi","ts":"1760839332600","msg":"hello from nifi"}
{"source":"nifi","ts":"1760839452604","msg":"hello from nifi"}
{"source":"nifi","ts":"1760839572607","msg":"hello from nifi"}
{"source":"nifi","ts":"1760839812614","msg":"hello from nifi"}
{"source":"nifi","ts":"1760839992619","msg":"hello from nifi"}
{"source":"nifi","ts":"1760840172626","msg":"hello from nifi"}
{"source":"nifi","ts":"1760840352632","msg":"hello from nifi"}
{"source":"nifi","ts":"1760840592639","msg":"hello from nifi"}
```
- Скрины результата в `de/ya/kafka/screens/task2/nifi-test_topic_check.png`

