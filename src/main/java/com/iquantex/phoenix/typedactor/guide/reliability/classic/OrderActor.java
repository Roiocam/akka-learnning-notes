package com.iquantex.phoenix.typedactor.guide.reliability.classic;

import com.iquantex.phoenix.typedactor.guide.reliability.protocol.OrderEvent;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.OrderEvent.OrderConfirmed;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.OrderEvent.OrderCreated;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.OrderMessage;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.OrderMessage.ConfirmOrder;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.OrderMessage.CreateOrder;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.OrderState;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.OrderState.OrderStatus;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.PaymentMessage.RequestPay;

import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.persistence.AbstractPersistentActorWithAtLeastOnceDelivery;
import akka.persistence.AtLeastOnceDelivery.UnconfirmedWarning;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;

/**
 * 处理订单的 Actor,
 *
 * <p>1. 接收客户端订单创建请求，向 {@link PaymentActor} 发起支付请求
 *
 * <p>2. 接收 {@link PaymentActor} 返回 {@link ConfirmOrder} 消息, 完成订单创建生命周期.
 *
 * <p>3. 若 2 不成功, 则每5秒执行一次 1 .
 *
 * @author AndyChen
 */
@Slf4j
public class OrderActor extends AbstractPersistentActorWithAtLeastOnceDelivery {

    private final ActorSelection destination;
    private OrderState state;

    public OrderActor(ActorSelection destination) {
        this.destination = destination;
        this.state = new OrderState(null, null);
    }

    public static Props create(ActorSelection paymentActorSelection) {
        return Props.create(OrderActor.class, () -> new OrderActor(paymentActorSelection));
    }

    @Override
    public Receive createReceiveRecover() {
        return receiveBuilder().match(OrderEvent.class, this::updateState).build();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(OrderMessage.CreateOrder.class, this::handleCreate)
                .match(OrderMessage.ConfirmOrder.class, this::handleConfirm)
                .match(UnconfirmedWarning.class, this::handleLimitDelivery)
                .build();
    }

    private void handleLimitDelivery(UnconfirmedWarning msg) {
        log.warn("接收到未确认警告,{}", msg.getUnconfirmedDeliveries());
    }

    @Override
    public String persistenceId() {
        return OrderActor.class.getSimpleName();
    }

    /**
     * 创建订单.
     *
     * @param cmd
     */
    public void handleCreate(CreateOrder cmd) {
        persist(new OrderCreated(cmd.getId()), evt -> updateState(evt));
    }

    /**
     * 确认订单
     *
     * @param cmd
     */
    public void handleConfirm(ConfirmOrder cmd) {
        persist(new OrderConfirmed(cmd.getId(), cmd.getDeliverId()), evt -> updateState(evt));
    }

    /**
     * 更新订单状态
     *
     * @param evt
     */
    public void updateState(OrderEvent evt) {
        if (evt instanceof OrderCreated) {
            OrderCreated created = (OrderCreated) evt;
            state = new OrderState(created.getId(), OrderStatus.Create);
            log.info("订单已创建,投递支付请求 {}", created.getId());
            // 可靠投递，如果没有接到 OrderConfirmed，则默认每 5s 继续投递一次
            deliver(destination, deliverId -> new RequestPay(created.getId(), deliverId));

        } else if (evt instanceof OrderConfirmed) {
            log.info("查看交付快照,{}", JSON.toJSONString(getDeliverySnapshot()));
            log.info("订单确认");
            OrderConfirmed confirmed = (OrderConfirmed) evt;
            state.setOrderStatus(OrderStatus.Confirm);
            // 确认投递成功
            confirmDelivery(confirmed.getDeliverId());
        }
    }
}
