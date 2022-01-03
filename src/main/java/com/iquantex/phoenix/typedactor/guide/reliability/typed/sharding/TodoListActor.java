package com.iquantex.phoenix.typedactor.guide.reliability.typed.sharding;

import com.iquantex.phoenix.typedactor.guide.reliability.mock.DB;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.TodoListMessage;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.TodoListMessage.AddTask;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.TodoListMessage.CommandDelivery;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.TodoListMessage.CompleteTask;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.TodoListMessage.DBError;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.TodoListMessage.InitialState;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.TodoListMessage.SaveSuccess;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.TodoState;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.delivery.ConsumerController;
import akka.actor.typed.delivery.ConsumerController.Start;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.ArrayList;

/** @author AndyChen */
public class TodoListActor extends AbstractBehavior<TodoListMessage> {

    private final String id;
    private final DB db;
    private TodoState state;
    private final ActorRef<Start<TodoListMessage>> consumerController;

    public TodoListActor(
            ActorContext<TodoListMessage> ctx,
            String id,
            DB db,
            ActorRef<Start<TodoListMessage>> consumerController) {
        super(ctx);
        this.id = id;
        this.db = db;
        this.state = new TodoState(new ArrayList<>());
        this.consumerController = consumerController;
    }

    public static Behavior<TodoListMessage> create(
            String id,
            DB db,
            ActorRef<ConsumerController.Start<TodoListMessage>> consumerController) {
        return Behaviors.setup(
                ctx -> {
                    ctx.pipeToSelf(
                            db.load(id), // 加载数据库
                            (state, ex) -> {
                                ctx.getLog().info("Actor ID={}", id);
                                if (ex == null) {
                                    return new InitialState(state);
                                } else {
                                    return new DBError(new RuntimeException(ex));
                                }
                            });
                    return new TodoListActor(ctx, id, db, consumerController);
                });
    }

    @Override
    public Receive<TodoListMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(InitialState.class, this::onInitialState)
                .onMessage(CommandDelivery.class, this::onDelivery)
                .onMessage(SaveSuccess.class, this::onSaveSuccess)
                .onMessage(DBError.class, this::onDBError)
                .build();
    }

    private Behavior<TodoListMessage> onInitialState(InitialState initial) {
        ActorRef<ConsumerController.Delivery<TodoListMessage>> deliveryAdapter =
                getContext()
                        .messageAdapter(
                                ConsumerController.deliveryClass(),
                                d -> new CommandDelivery(d.message(), d.confirmTo()));
        getContext().getLog().info("TodoList 初始化,{}.{}", initial, deliveryAdapter.path());
        consumerController.tell(new ConsumerController.Start<>(deliveryAdapter));
        return Behaviors.same();
    }

    private Behavior<TodoListMessage> onDelivery(CommandDelivery delivery) {
        if (delivery.getCommand() instanceof AddTask) {
            AddTask addTask = (AddTask) delivery.getCommand();
            getContext().getLog().info("TodoList 已拉取到任务,{}", addTask.getItem());
            state = state.add(addTask.getItem());
            save(state, delivery.getConfirmTo());
            return this;
        } else if (delivery.getCommand() instanceof CompleteTask) {
            CompleteTask completeTask = (CompleteTask) delivery.getCommand();
            getContext().getLog().info("TodoList 已拉取到任务,{}", completeTask.getItem());
            state = state.remove(completeTask.getItem());
            save(state, delivery.getConfirmTo());
            return this;
        } else {
            return Behaviors.unhandled();
        }
    }

    private void save(TodoState newState, ActorRef<ConsumerController.Confirmed> confirmTo) {
        getContext()
                .pipeToSelf(
                        db.save(id, newState),
                        (state, exc) -> {
                            if (exc == null) {
                                return new SaveSuccess(confirmTo);
                            } else {
                                return new DBError(new Exception(exc));
                            }
                        });
    }

    private Behavior<TodoListMessage> onSaveSuccess(SaveSuccess success) {
        success.getConfirmedTo().tell(ConsumerController.confirmed());
        return this;
    }

    private Behavior<TodoListMessage> onDBError(DBError error) throws Exception {
        throw error.getCause();
    }
}
