package com.iquantex.phoenix.typedactor.guide.reliability.protocol;

import akka.actor.Props;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import scala.sys.Prop;

/**
 * @author AndyChen
 */
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
