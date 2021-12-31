package com.iquantex.phoenix.typedactor.guide.reliability.typed.p2p;

import com.iquantex.phoenix.typedactor.guide.reliability.protocol.OrderEvent;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.OrderEvent.OrderConfirmed;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.OrderEvent.OrderCreated;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.OrderMessage;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.OrderMessage.ConfirmOrder;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.OrderMessage.CreateOrder;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.OrderMessage.WrappedRequestNext;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.OrderState;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.OrderState.OrderStatus;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.PaymentMessage;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.PaymentMessage.RequestTypedPay;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.delivery.ProducerController;
import akka.actor.typed.delivery.ProducerController.RequestNext;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.Effect;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.LinkedBlockingQueue;

/** @author AndyChen */
@Slf4j
public class OrderActor extends EventSourcedBehavior<OrderMessage, OrderEvent, OrderState> {

    private ActorContext<OrderMessage> ctx;
    private ActorRef<ProducerController.Command<PaymentMessage>> producerController;
    private ActorRef<ProducerController.RequestNext<PaymentMessage>> requestNextAdapter;
    private LinkedBlockingQueue<PaymentMessage> queue = new LinkedBlockingQueue<>();

    public OrderActor(
            PersistenceId persistenceId,
            ActorContext context,
            ActorRef<ProducerController.Command<PaymentMessage>> producerController,
            ActorRef<ProducerController.RequestNext<PaymentMessage>> requestNextAdapter) {
        super(persistenceId);
        this.producerController = producerController;
        this.ctx = context;
        this.requestNextAdapter = requestNextAdapter;
    }

    public static Behavior<OrderMessage> create(
            String id, ActorRef<ProducerController.Command<PaymentMessage>> producerController) {
        return Behaviors.setup(
                context -> {
                    ActorRef<ProducerController.RequestNext<PaymentMessage>> requestNextAdapter =
                            context.messageAdapter(
                                    ProducerController.requestNextClass(), WrappedRequestNext::new);
                    // 开启可靠投递, 等待 Consumer 准备好
                    producerController.tell(new ProducerController.Start<>(requestNextAdapter));
                    return new OrderActor(
                            PersistenceId.ofUniqueId(id),
                            context,
                            producerController,
                            requestNextAdapter);
                });
    }

    @Override
    public OrderState emptyState() {
        return new OrderState(null, null);
    }

    @Override
    public CommandHandler<OrderMessage, OrderEvent, OrderState> commandHandler() {
        return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(OrderMessage.CreateOrder.class, this::handleCreate)
                .onCommand(OrderMessage.ConfirmOrder.class, this::handleConfirm)
                .onCommand(OrderMessage.WrappedRequestNext.class, this::handleSend)
                .build();
    }

    /**
     * 接收订单创建
     *
     * @param state
     * @param cmd
     * @return
     */
    private Effect<OrderEvent, OrderState> handleCreate(OrderState state, CreateOrder cmd) {
        return Effect().persist(new OrderCreated(cmd.getId()));
    }
    /**
     * 当 Consumer 准备好之后，才会发送消息.
     *
     * @param next
     * @return
     */
    private Effect<OrderEvent, OrderState> handleSend(WrappedRequestNext next) {
        RequestNext<PaymentMessage> nextNext = next.getNext();
        PaymentMessage poll = queue.poll();
        if (poll != null) {
            log.info("待发送消息不为空");
            nextNext.sendNextTo().tell(poll);
        }
        return Effect().none();
    }

    /**
     * 接收支付确认
     *
     * @param state
     * @param cmd
     * @return
     */
    private Effect<OrderEvent, OrderState> handleConfirm(OrderState state, ConfirmOrder cmd) {
        return Effect().persist(new OrderConfirmed(cmd.getId(), cmd.getDeliverId()));
    }

    @Override
    public EventHandler<OrderState, OrderEvent> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(OrderEvent.class, this::updateState)
                .build();
    }

    /**
     * 更新 Actor 状态, 在这里实现了待发送列表的持久化, 通过事件日志的方式实现.
     *
     * @param state
     * @param evt
     * @return
     */
    public OrderState updateState(OrderState state, OrderEvent evt) {
        if (evt instanceof OrderCreated) {
            OrderCreated created = (OrderCreated) evt;
            log.info("订单已创建,投递支付请求 {}", created.getId());
            queue.add(new RequestTypedPay(created.getId(), ctx.getSelf()));
            return new OrderState(created.getId(), OrderStatus.Create);
        } else if (evt instanceof OrderConfirmed) {
            log.info("订单确认");
            state.setOrderStatus(OrderStatus.Confirm);
            return state;
        } else {
            return state;
        }
    }
}
