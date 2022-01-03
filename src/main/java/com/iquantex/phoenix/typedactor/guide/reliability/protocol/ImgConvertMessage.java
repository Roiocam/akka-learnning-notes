package com.iquantex.phoenix.typedactor.guide.reliability.protocol;

import akka.actor.typed.delivery.ConsumerController;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;

public interface ImgConvertMessage extends Serializable {

    @AllArgsConstructor
    @Getter
    class WrapperDelivery implements ImgConvertMessage {
        private final ConsumerController.Delivery<ConversionJob> delivery;
    }
}
