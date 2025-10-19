package de.ya.kafka;

import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class ConsumerApp {

    public static void main(String[] args) throws Exception {
        Properties props = loadProps();

        props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, props.getProperty("consumer.group", "sr-json-consumer"));
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaJsonSchemaDeserializer.class.getName());

        // POJO для JSON Schema десериализации
        props.putIfAbsent("json.value.type", NifiMessage.class.getName());
        // (Опционально) строгая проверка схемы
        props.putIfAbsent("json.fail.invalid.schema", "true");

        final String topic = props.getProperty("topic.name", "ya-kafka-task-7");
        System.out.println("Consumer -> groupId=" + props.getProperty("group.id") + ", topic=" + topic);

        try (KafkaConsumer<String, NifiMessage> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));
            System.out.println("Consuming from topic: " + topic);

            while (true) {
                ConsumerRecords<String, NifiMessage> records = consumer.poll(Duration.ofSeconds(2));
                for (ConsumerRecord<String, NifiMessage> r : records) {
                    System.out.printf("Got record: partition=%d offset=%d key=%s value=%s%n",
                            r.partition(), r.offset(), r.key(), r.value());
                }
            }
        }
    }

    private static Properties loadProps() throws Exception {
        Properties p = new Properties();

        // 1) -Dapp.config
        String sys = System.getProperty("app.config");
        if (notBlank(sys)) {
            try (InputStream in = new FileInputStream(sys)) {
                p.load(in);
                System.out.println("Loaded config from -Dapp.config=" + sys);
                return p;
            }
        }

        // 2) env KAFKA_CLIENT_PROPS
        String env = System.getenv("KAFKA_CLIENT_PROPS");
        if (notBlank(env)) {
            try (InputStream in = new FileInputStream(env)) {
                p.load(in);
                System.out.println("Loaded config from $KAFKA_CLIENT_PROPS=" + env);
                return p;
            }
        }

        // 3) classpath
        try (InputStream in = ConsumerApp.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                p.load(in);
                System.out.println("Loaded config from classpath: application.properties");
                return p;
            }
        }

        // 4) ./application.properties
        try (InputStream in = new FileInputStream("./application.properties")) {
            p.load(in);
            System.out.println("Loaded config from ./application.properties");
            return p;
        } catch (Exception ignored) {}

        throw new IllegalStateException(
                "Не найден application.properties. Укажи путь через -Dapp.config=..., " +
                "или переменную окружения KAFKA_CLIENT_PROPS, " +
                "или положи файл в resources (classpath) / в текущую директорию.");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
