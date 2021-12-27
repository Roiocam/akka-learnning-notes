package com.iquantex.phoenix.typedactor.guide.reliability.protocol;

import com.iquantex.phoenix.typedactor.guide.protocol.CborSerializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public interface OrderEvent extends CborSerializable {

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    class OrderCreated implements OrderEvent {

        private String id;

    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    class OrderConfirmed implements OrderEvent {

        private String id;
        private Long deliverId;

    }
}
