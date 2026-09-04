package com.softdesign.votacao.kafka.producer;

import com.softdesign.votacao.kafka.event.VotoSolicitadoEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class VotoProducer {

    private final KafkaTemplate<String, VotoSolicitadoEvent> kafkaTemplate;

    @Value("${kafka.voto.topic}")
    private String votoTopic;

    public CompletableFuture<SendResult<String, VotoSolicitadoEvent>> enviar(VotoSolicitadoEvent evento) {
        return kafkaTemplate.send(votoTopic, evento.votoId().toString(), evento);
    }

}
