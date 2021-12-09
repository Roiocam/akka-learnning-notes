package com.iquantex.phoenix.typedactor.guide.actor;

import com.iquantex.phoenix.typedactor.guide.protocol.CborSerializable;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.*;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** @author AndyChen */
public class MarketDataActor
        extends EventSourcedBehavior<
                MarketDataActor.Message, MarketDataActor.Event, MarketDataActor.State> {

    public MarketDataActor(PersistenceId persistenceId, ActorContext<Void> context) {
        super(persistenceId);
        this.context = context;
    }

    private final ActorContext<Void> context;

    public static Behavior<MarketDataActor.Message> create(
            PersistenceId persistenceId, ActorContext<Void> context) {
        return Behaviors.setup(ctx -> new MarketDataActor(persistenceId, context));
    }

    @Override
    public State emptyState() {
        return new LiveState(persistenceId().id(), 0L);
    }

    @Override
    public CommandHandler<Message, Event, State> commandHandler() {
        CommandHandlerBuilder<Message, Event, State> builder = newCommandHandlerBuilder();

        builder.forStateType(LiveState.class)
                .onCommand(
                        UpdateValue.class,
                        (state, message) ->
                                Effect()
                                        .persist(
                                                new UpdateEvent(
                                                        state.getId(),
                                                        state.getValue() + message.getValue()))
                                        .thenReply(message.getReplyRef(), temp -> temp))
                .onCommand(
                        TrialUpdateValue.class,
                        (state, message) ->
                                Effect()
                                        .persist(
                                                new TrialUpdateEvent(
                                                        state.getId(),
                                                        state.getValue() + message.getValue()))
                                        .thenReply(message.getReplyRef(), temp -> temp))
                .onCommand(Query.class, (state, query) -> Effect().reply(query.getReply(), state));

        // 需要取 Live
        builder.forStateType(TrialState.class)
                .onCommand(
                        UpdateValue.class,
                        (state, message) ->
                                Effect()
                                        .persist(
                                                new UpdateEvent(
                                                        state.liveState.getId(),
                                                        state.liveState.getValue()
                                                                + message.getValue()))
                                        .thenReply(message.getReplyRef(), temp -> temp))
                .onCommand(
                        TrialUpdateValue.class,
                        (state, message) ->
                                Effect()
                                        .persist(
                                                new TrialUpdateEvent(
                                                        state.liveState.getId(),
                                                        state.liveState.getValue()
                                                                + message.getValue()))
                                        .thenReply(message.getReplyRef(), temp -> temp))
                .onCommand(
                        Query.class,
                        (state, query) -> Effect().reply(query.getReply(), state.liveState));

        builder.forAnyState()
                .onCommand(
                        UpdateValue.class,
                        (state, message) ->
                                Effect()
                                        .persist(
                                                new UpdateEvent(
                                                        state.getId(),
                                                        state.getValue() + message.getValue()))
                                        .thenReply(message.getReplyRef(), temp -> temp))
                .onCommand(
                        TrialUpdateValue.class,
                        (state, message) ->
                                Effect()
                                        .persist(
                                                new TrialUpdateEvent(
                                                        state.getId(),
                                                        state.getValue() + message.getValue()))
                                        .thenReply(message.getReplyRef(), temp -> temp))
                .onCommand(Query.class, (state, query) -> Effect().reply(query.getReply(), state));
        return builder.build();
    }

    public ActorRef<Message> getChild(ActorContext context) {
        return context.spawn(
                MarketDataActor.create(PersistenceId.ofUniqueId("asd"), context), "temp");
    }

    public State newState(
            TrialUpdateValue message, State old, Scheduler scheduler, ActorRef<Message> child) {
        CompletableFuture<State> future =
                AskPattern.<MarketDataActor.Message, MarketDataActor.State>ask(
                                child,
                                reply -> new UpdateValue(message.value, message.getReplyRef()),
                                Duration.ofSeconds(1),
                                scheduler)
                        .toCompletableFuture();
        try {
            return future.get(1, TimeUnit.SECONDS);

        } catch (Exception e) {
            return old;
        }
    }

    @Override
    public EventHandler<State, Event> eventHandler() {
        EventHandlerBuilder<State, Event> builder = newEventHandlerBuilder();

        builder.forStateType(LiveState.class)
                .onEvent(
                        UpdateEvent.class,
                        (state, updateEvent) ->
                                new LiveState(updateEvent.getId(), updateEvent.getValue())) // 更新
                .onEvent(
                        TrialUpdateEvent.class,
                        (state, updateEvent) -> {
                            System.out.println("我持久化了");
                            return new TrialState(
                                    updateEvent.getId(), updateEvent.getValue(), state);
                        }); // 更新，暂存原来的

        builder.forStateType(TrialState.class)
                .onEvent(
                        UpdateEvent.class,
                        (state, updateEvent) ->
                                new LiveState(
                                        state.liveState.getId(), state.liveState.getValue())) // 还原
                .onEvent(
                        TrialUpdateEvent.class,
                        (state, updateEvent) ->
                                new TrialState(
                                        updateEvent.getId(),
                                        updateEvent.getValue(),
                                        state.liveState)); // 更新，保持原来的不变

        builder.forAnyState()
                .onEvent(
                        UpdateEvent.class,
                        (state, updateEvent) ->
                                new LiveState(updateEvent.getId(), updateEvent.getValue()))
                .onEvent(
                        TrialUpdateEvent.class,
                        (state, updateEvent) ->
                                new TrialState(
                                        updateEvent.getId(),
                                        updateEvent.getValue(),
                                        state)); // 更新，暂存原来的
        return builder.build();
    }

    public interface Message extends CborSerializable {}

    @AllArgsConstructor
    @Getter
    public static class UpdateValue implements Message {

        private Long value;
        private ActorRef<State> replyRef;
    }

    @AllArgsConstructor
    @Getter
    public static class TrialUpdateValue implements Message {

        private Long value;
        private ActorRef<State> replyRef;
    }

    @AllArgsConstructor
    @Getter
    public static class Query implements Message {

        private ActorRef<State> replyRef;

        public ActorRef<State> getReply() {
            return replyRef;
        }
    }

    interface Event {}

    @AllArgsConstructor
    @Getter
    class UpdateEvent implements Event {

        private String id;
        private Long value;
    }

    @AllArgsConstructor
    @Getter
    class TrialUpdateEvent implements Event {

        private String id;
        private Long value;
    }

    @AllArgsConstructor
    @Getter
    public abstract class State {

        private String id;
        private Long value;
    }

    @Getter
    public final class LiveState extends State {

        public LiveState(String id, Long value) {
            super(id, value);
        }
    }

    @Getter
    public final class TrialState extends State {

        private State liveState;

        public TrialState(String id, Long value, State state) {
            super(id, value);
            this.liveState = state;
        }
    }
}
