package com.iquantex.phoenix.typedactor.guide.reliability.protocol;

import com.iquantex.phoenix.typedactor.guide.protocol.CborSerializable;

import akka.actor.typed.ActorRef;
import akka.actor.typed.delivery.ConsumerController;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** @author AndyChen */
public interface PaymentMessage extends CborSerializable {

    @AllArgsConstructor
    @Getter
    class RequestPay implements PaymentMessage {

        private String id;
        private Long deliverId;
    }

    @AllArgsConstructor
    @Getter
    class RequestTypedPay implements PaymentMessage {

        private String id;
        private ActorRef replyTo;
    }

    @AllArgsConstructor
    @Getter
    class PaySuccess implements PaymentMessage {

        private Long deliverId;
        private String id;
    }

    @AllArgsConstructor
    @Getter
    class PayTypedSuccess implements PaymentMessage {

        private String id;
        private ActorRef replyTo;
    }

    @AllArgsConstructor
    @Getter
    class WrappedResponseNext implements PaymentMessage {

        private ConsumerController.Delivery<PaymentMessage> next;
    }
}
