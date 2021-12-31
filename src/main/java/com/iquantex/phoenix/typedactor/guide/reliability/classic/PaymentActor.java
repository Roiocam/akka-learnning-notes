package com.iquantex.phoenix.typedactor.guide.reliability.classic;

import com.iquantex.phoenix.typedactor.guide.reliability.protocol.OrderMessage.ConfirmOrder;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.PaymentMessage.PaySuccess;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.PaymentMessage.RequestPay;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Random;

/**
 * 支付 Actor
 *
 * <p>1. 接收 {@link OrderActor} 发起的{@link RequestPay} 消息, 向 {@link } 发起支付任务
 *
 * <p>2. 接收 {@link } 支付任务的成功结果, 并向缓存的 {@link OrderActor} 回复支付成功的确认
 *
 * @author AndyChen
 */
@Slf4j
public class PaymentActor extends AbstractActor {

    /** 缓存支付中订单 */
    private Map<String, ActorRef> cacheSender = Maps.newHashMap();

    private Integer receiverCounter = 0;
    private Integer responseCounter = 0;
    private Integer receiverLimit = new Random().nextInt(2) + 2;
    private Integer responseLimit = new Random().nextInt(2) + 2;

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

    private void handlePayConfirm(PaySuccess msg) {
        if (++responseCounter >= responseLimit) {
            log.info("支付成功,{},{}", msg.getId(), msg.getDeliverId());
            cacheSender
                    .remove(msg.getId())
                    .tell(new ConfirmOrder(msg.getId(), msg.getDeliverId()), getSelf());
        } else {
            log.info("已接收消息, 模拟服务端 -> 客户端消息丢失, [{} != {}]", responseCounter, responseLimit);
        }
    }

    private void handlePaymentRequest(RequestPay msg) {
        if (++receiverCounter >= receiverLimit) {
            log.info("接收到[{}]支付请求,{} =  {}", getSender().path(), msg.getId(), msg.getDeliverId());
            // 缓存发送者
            cacheSender.put(msg.getId(), getSender());
            getSelf().tell(new PaySuccess(msg.getDeliverId(), msg.getId()), getSelf());

        } else {
            log.info("接收到消息，模拟客户端 -> 服务端消息丢失 ,[{} != {}]", receiverCounter, receiverLimit);
        }
    }
}
