package com.iquantex.phoenix.typedactor.guide.reliability.protocol;

import akka.actor.typed.ActorRef;
import akka.actor.typed.delivery.WorkPullingProducerController;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Optional;
import java.util.UUID;

/** @author AndyChen */
public interface ImgWorkMessage {

    @AllArgsConstructor
    @Getter
    class Covert implements ImgWorkMessage {

        private final String fromFormat;
        private final String toFormat;
        private final byte[] image;
    }

    @AllArgsConstructor
    @Getter
    class GetResult implements ImgWorkMessage {

        private final UUID resultId;
        private final ActorRef<Optional<byte[]>> replyTo;
    }

    @AllArgsConstructor
    @Getter
    class WrapperRequestNext implements ImgWorkMessage {

        private final WorkPullingProducerController.RequestNext<ConversionJob> next;
    }
}
