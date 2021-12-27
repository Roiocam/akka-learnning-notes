package com.iquantex.phoenix.typedactor.guide.reliability.protocol;

import com.iquantex.phoenix.typedactor.guide.protocol.CborSerializable;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author AndyChen
 */
public interface PaymentMessage extends CborSerializable {

    @AllArgsConstructor
    @Getter
    class RequestPay implements PaymentMessage {

        private String id;
        private Long deliverId;

    }

    @AllArgsConstructor
    @Getter
    class PaySuccess implements PaymentMessage {

        private Long deliverId;
        private String id;

    }
}
