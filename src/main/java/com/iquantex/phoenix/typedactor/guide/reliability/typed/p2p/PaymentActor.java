package com.iquantex.phoenix.typedactor.guide.reliability.typed.p2p;

import com.iquantex.phoenix.typedactor.guide.reliability.protocol.OrderMessage.ConfirmOrder;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.PaymentMessage;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.PaymentMessage.PayTypedSuccess;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.PaymentMessage.RequestTypedPay;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.PaymentMessage.WrappedResponseNext;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.delivery.ConsumerController;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/** @author AndyChen */
@Slf4j
public class PaymentActor extends AbstractBehavior<PaymentMessage> {

    private Integer receiverCounter = 0;
    private Integer responseCounter = 0;
    private Integer receiverLimit = new Random().nextInt(2) + 2;
    private Integer responseLimit = new Random().nextInt(2) + 2;

    /** 缓存支付中订单 */
    private Map<String, ActorRef> cacheSender = Maps.newHashMap();

    private ActorContext<PaymentMessage> ctx;

    public PaymentActor(ActorContext<PaymentMessage> context) {
        super(context);
        this.ctx = context;
    }

    public static Behavior<PaymentMessage> create(
            ActorRef<ConsumerController.Command<PaymentMessage>> consumerController) {
        return Behaviors.setup(
                context -> {
                    ActorRef<ConsumerController.Delivery<PaymentMessage>> deliveryAdapter =
                            context.messageAdapter(
                                    ConsumerController.deliveryClass(), WrappedResponseNext::new);
                    consumerController.tell(new ConsumerController.Start<>(deliveryAdapter));
                    return new PaymentActor(context);
                });
    }

    @Override
    public Receive<PaymentMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(PaymentMessage.RequestTypedPay.class, this::handlePaymentRequest)
                .onMessage(PaymentMessage.PayTypedSuccess.class, this::handlePaySuccess)
                .onMessage(WrappedResponseNext.class, this::handleRequest)
                .build();
    }

    /**
     * 接到消息后处理
     *
     * @param request
     * @return
     */
    private Behavior<PaymentMessage> handleRequest(WrappedResponseNext request)
            throws InterruptedException {

        log.info("接收到 Producer 投递的消息, 模拟回复确认时网络延迟 1s");
        CompletableFuture.runAsync(
                () -> {
                    try {
                        // 这里是异步的，因而，不会阻塞当前 Actor 接收消息 Mailbox，只有 Consumer 显式确认之后 Producer 才会发送第二条消息
                        Thread.sleep(1000);
                        log.info("模拟延迟 1s 后，确认消息到达生产者端。此时消费者拉取下一条待发送消息");
                        // 告诉 Producer 自己已经接收到消息了，可以发送第二个消息过来
                        request.getNext().confirmTo().tell(ConsumerController.confirmed());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
        // 转发给自身
        getContext().getSelf().tell(request.getNext().message());
        return Behaviors.same();
    }

    private Behavior<PaymentMessage> handlePaySuccess(PayTypedSuccess msg) {
        log.info("支付成功,{}", msg.getId());
        msg.getReplyTo().tell(new ConfirmOrder(msg.getId(), null));
        return Behaviors.same();
    }

    private Behavior<PaymentMessage> handlePaymentRequest(RequestTypedPay msg) {
        log.info("接收到转发的[{}]支付请求,{}", msg.getReplyTo().path(), msg.getId());
        ctx.getSelf().tell(new PayTypedSuccess(msg.getId(), msg.getReplyTo()));
        return Behaviors.same();
    }
}
