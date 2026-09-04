package com.softdesign.votacao.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableKafkaRetryTopic
@EnableScheduling
public class KafkaConfig {

    @Bean
    public NewTopic votoSolicitadoTopic(
        @Value("${kafka.voto.topic}") String topic,
        @Value("${kafka.voto.partitions}") int partitions
    ) {
        return TopicBuilder.name(topic)
            .partitions(partitions)
            .replicas(1)
            .build();
    }

    @Bean
    public TaskScheduler kafkaRetryTopicScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("kafka-retry-");
        return scheduler;
    }

}
