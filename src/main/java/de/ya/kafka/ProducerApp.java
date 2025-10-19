package de.ya.kafka;

import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Objects;
import java.util.Properties;

public class ProducerApp {

    public static void main(String[] args) throws Exception {
        Properties props = loadProps();

        // Явно задаём сериализаторы
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSchemaSerializer.class.getName());

        // JSON Schema: используем уже зарегистрированную схему
        props.putIfAbsent("latest.compatibility.strict", "false");
        props.putIfAbsent("auto.register.schemas", "false");
        props.putIfAbsent("use.latest.version", "true");
        props.putIfAbsent("json.fail.invalid.schema", "true");

        final String topic = props.getProperty("topic.name", "ya-kafka-task-7");
        System.out.println("Producer -> topic: " + topic);

        try (Producer<String, NifiMessage> producer = new KafkaProducer<>(props)) {
            for (int i = 1; i <= 5; i++) {
                String key = "device-" + (i % 2 == 0 ? 2 : 1); // два стабильных ключа
                NifiMessage value = new NifiMessage("nifi", Instant.now().toString(), "hello-" + i);

                // final-копии для callback-лямбды
                final String keyCopy = key;
                final NifiMessage valueCopy = value;

                ProducerRecord<String, NifiMessage> record = new ProducerRecord<>(topic, keyCopy, valueCopy);
                producer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        System.err.println("Send failed: " + exception.getMessage());
                    } else {
                        System.out.printf("Produced to %s-%d @ offset %d, key=%s, value=%s%n",
                                metadata.topic(), metadata.partition(), metadata.offset(), keyCopy, valueCopy);
                    }
                });
            }
            producer.flush();
            System.out.println("Producer -> all messages flushed.");
        }
    }

    private static Properties loadProps() throws Exception {
        Properties p = new Properties();

        // 1) -Dapp.config=/path/to/application.properties
        String sys = System.getProperty("app.config");
        if (notBlank(sys)) {
            try (InputStream in = new FileInputStream(sys)) {
                p.load(in);
                System.out.println("Loaded config from -Dapp.config=" + sys);
                return p;
            }
        }

        // 2) env KAFKA_CLIENT_PROPS=/path/to/application.properties
        String env = System.getenv("KAFKA_CLIENT_PROPS");
        if (notBlank(env)) {
            try (InputStream in = new FileInputStream(env)) {
                p.load(in);
                System.out.println("Loaded config from $KAFKA_CLIENT_PROPS=" + env);
                return p;
            }
        }

        // 3) classpath: application.properties
        try (InputStream in = ProducerApp.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                p.load(in);
                System.out.println("Loaded config from classpath: application.properties");
                return p;
            }
        }

        // 4) ./application.properties (рядом с target/classes)
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
