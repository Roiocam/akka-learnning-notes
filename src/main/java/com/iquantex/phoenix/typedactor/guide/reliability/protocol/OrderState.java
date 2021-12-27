package com.iquantex.phoenix.typedactor.guide.reliability.protocol;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** @author AndyChen */
@AllArgsConstructor
@Getter
@Setter
public class OrderState {

    private String orderId;
    private OrderStatus orderStatus;

    @AllArgsConstructor
    public enum OrderStatus {
        Create,
        Confirm
    }
}
