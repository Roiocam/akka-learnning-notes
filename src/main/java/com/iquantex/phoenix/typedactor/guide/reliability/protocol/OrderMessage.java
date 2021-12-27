package com.iquantex.phoenix.typedactor.guide.reliability.protocol;

import com.iquantex.phoenix.typedactor.guide.protocol.CborSerializable;
import lombok.AllArgsConstructor;
import lombok.Getter;

public interface OrderMessage extends CborSerializable {

    @AllArgsConstructor
    @Getter
    class CreateOrder implements OrderMessage {

        private String id;

    }


    @AllArgsConstructor
    @Getter
    class ConfirmOrder implements OrderMessage {

        private String id;
        private Long deliverId;
    }
}
