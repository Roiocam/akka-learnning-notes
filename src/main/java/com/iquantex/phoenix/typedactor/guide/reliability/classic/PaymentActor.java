package com.iquantex.phoenix.typedactor.guide.reliability.classic;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import com.google.common.collect.Maps;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.OrderMessage.ConfirmOrder;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.PaymentMessage.PaySuccess;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.PaymentMessage.RequestPay;
import com.iquantex.phoenix.typedactor.guide.reliability.service.PaymentService;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 支付 Actor
 *
 * <p>1. 接收 {@link OrderActor} 发起的{@link RequestPay} 消息, 向 {@link PaymentService} 发起支付任务</p>
 * <p>2. 接收 {@link PaymentService} 支付任务的成功结果, 并向缓存的 {@link OrderActor} 回复支付成功的确认</p>
 *
 * @author AndyChen
 */
@Slf4j
public class PaymentActor extends AbstractActor {


    /**
     * 缓存支付中订单
     */
    private Map<String, ActorRef> cacheSender = Maps.newHashMap();

    public static Props create() {
        return Props.create(PaymentActor.class, () -> new PaymentActor());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(RequestPay.class, this::handlePaymentRequest)
            .match(PaySuccess.class, this::handlePayConfirm)
            .build();
    }

    /**
     * 支付成功时接收并处理 {@link PaymentService} 的通知，向上游发起订单确认
     *
     * @param msg
     */
    private void handlePayConfirm(PaySuccess msg) {
        log.info("支付成功,{},{}", msg.getId(), msg.getDeliverId());
        cacheSender.remove(msg.getId())
            .tell(new ConfirmOrder(msg.getId(), msg.getDeliverId()), getSelf());
    }

    /**
     * 处理支付请求，向{@link PaymentService} 发起支付请求. 此方法通过 {@link java.util.HashMap} 实现幂等
     *
     * @param msg
     */
    private void handlePaymentRequest(RequestPay msg) {
        // 幂等处理, 只向 PaymentService 发起一次请求
        if (!cacheSender.containsKey(msg.getId())) {
            PaymentService.createPayTask(msg.getDeliverId(), msg.getId(), getSelf());
        }
        cacheSender.putIfAbsent(msg.getId(), getSender());
        log.info("接收到[{}]支付请求,{} =  {}", getSender().path(), msg.getId(), msg.getDeliverId());
    }
}
