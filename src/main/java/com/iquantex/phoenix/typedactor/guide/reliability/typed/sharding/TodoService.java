package com.iquantex.phoenix.typedactor.guide.reliability.typed.sharding;

import com.iquantex.phoenix.typedactor.guide.reliability.protocol.TodoListMessage;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.TodoListMessage.AddTask;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.TodoListMessage.CompleteTask;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.TodoResponse;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.TodoServiceMessage;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.TodoServiceMessage.Confirmed;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.TodoServiceMessage.TimedOut;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.TodoServiceMessage.UpdateTodo;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.TodoServiceMessage.WrappedRequestNext;

import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.cluster.sharding.typed.delivery.ShardingProducerController;
import akka.cluster.sharding.typed.delivery.ShardingProducerController.Command;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/** @author AndyChen */
@Slf4j
public class TodoService extends AbstractBehavior<TodoServiceMessage> {

    private ShardingProducerController.RequestNext<TodoListMessage> requestNext;

    public TodoService(ActorContext<TodoServiceMessage> context) {
        super(context);
    }

    public static Behavior<TodoServiceMessage> create(
            ActorRef<Command<TodoListMessage>> producerController) {
        return Behaviors.setup(
                ctx -> {
                    ActorRef<ShardingProducerController.RequestNext<TodoListMessage>>
                            requestNextAdapter =
                                    ctx.messageAdapter(
                                            ShardingProducerController.requestNextClass(),
                                            WrappedRequestNext::new);
                    producerController.tell(
                            new ShardingProducerController.Start<>(requestNextAdapter));
                    return new TodoService(ctx);
                });
    }

    @Override
    public Receive<TodoServiceMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(
                        WrappedRequestNext.class,
                        w -> {
                            getContext()
                                    .getLog()
                                    .info("接收工作拉取,来自={}", w, w.getNext().askNextTo().path());
                            this.requestNext = w.getNext();
                            return this;
                        })
                .onMessage(UpdateTodo.class, this::onUpdateTodo)
                .onMessage(Confirmed.class, this::onConfirmed)
                .onMessage(TimedOut.class, this::onTimedOut)
                .build();
    }

    private Behavior<TodoServiceMessage> onUpdateTodo(UpdateTodo command) {
        Integer buffered =
                requestNext.getBufferedForEntitiesWithoutDemand().get(command.getListId());
        if (buffered != null && buffered >= 100) {
            command.getReplyTo().tell(TodoResponse.REJECTED);
        } else {
            TodoListMessage requestMsg;
            if (command.isCompleted()) {
                requestMsg = new CompleteTask(command.getItem());
            } else {
                requestMsg = new AddTask(command.getItem());
            }
            getContext()
                    .getLog()
                    .info("Service 推送消息给工作拉取消费者={}，消息={}", command.getListId(), requestMsg);
            getContext()
                    .ask(
                            Done.class,
                            requestNext.askNextTo(),
                            Duration.ofSeconds(5),
                            askReplyTo ->
                                    new ShardingProducerController.MessageWithConfirmation<>(
                                            command.getListId(), requestMsg, askReplyTo),
                            (done, exc) -> {
                                if (exc == null) {
                                    return new Confirmed(command.getReplyTo());
                                } else {
                                    return new TimedOut(command.getReplyTo());
                                }
                            });
        }
        return this;
    }

    private Behavior<TodoServiceMessage> onConfirmed(Confirmed confirmed) {
        confirmed.getOriginalReplyTo().tell(TodoResponse.ACCEPTED);
        return this;
    }

    private Behavior<TodoServiceMessage> onTimedOut(TimedOut timedOut) {
        timedOut.getOriginalReplyTo().tell(TodoResponse.MAYBE_ACCEPTED);
        return this;
    }
}
