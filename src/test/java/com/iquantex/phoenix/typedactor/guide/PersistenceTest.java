package com.iquantex.phoenix.typedactor.guide;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.ActorRef;
import akka.actor.typed.BackoffSupervisorStrategy;
import akka.actor.typed.Behavior;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.Effect;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import com.google.common.collect.Lists;
import com.iquantex.phoenix.typedactor.guide.protocol.CborSerializable;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import jdk.jfr.DataAmount;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.junit.ClassRule;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.Callable;

public class PersistenceTest {

    @ClassRule
    private TestKitJunitResource testkit = new TestKitJunitResource(EventSourcedBehaviorTestKit.config());

    public static void main(String[] args) {
        Config load = ConfigFactory.load("reference-persistence.conf");
        System.out.println(load.getObject("akka.actor.serialization-bindings"));
    }

    private RemoteService remoteService = new RemoteService();

    private EventSourcedBehaviorTestKit<Command, Event, String> eventSouredTestkit = EventSourcedBehaviorTestKit.create(testkit.system(), new PersistActor(PersistenceId.ofUniqueId("persistActor_01"), remoteService));


    public void beforeEach() {
        eventSouredTestkit.clear();
    }

    @Test
    public void test() {

        EventSourcedBehaviorTestKit.CommandResultWithReply<Command, Event, String, String> asd = eventSouredTestkit.runCommand(actorRef -> new UpdateCommand("asd", actorRef));

        List<Event> events = asd.events();


        String state = asd.state();

        System.out.println(events);
        System.out.println(state);

        EventSourcedBehaviorTestKit.RestartResult<String> restart = eventSouredTestkit.restart();

        String state2 = restart.state();

        System.out.println(state2);

    }

    interface Command extends akka.testkit.JavaSerializable {
    }

    @Data
    @Builder
    static class UpdateCommand implements Command {
        private String newState;
        private ActorRef<String> reply;
    }

    interface Event extends akka.testkit.JavaSerializable {
    }

    @Data
    @Builder
    static class UpdateEvent implements Event {
        private String updateState;
    }

    @Data
    @Builder
    static class CacheInvoke implements Event {
        private Map<String, Object> cacheMap;
    }

    static class RemoteService {

        public String rpcState() {
            return "rpcState";
        }
    }

    static class PersistActor extends EventSourcedBehavior<Command, Event, String> {

        private RemoteService remoteService;

        public PersistActor(PersistenceId persistenceId, RemoteService remoteService) {
            super(persistenceId);
            this.remoteService = remoteService;
        }


        @Override
        public String emptyState() {
            return "";
        }

        @Override
        public CommandHandler<Command, Event, String> commandHandler() {
            return newCommandHandlerBuilder().forAnyState().onCommand(UpdateCommand.class, this::handleUpdateCmd).build();
        }

        private Effect<Event, String> handleUpdateCmd(String state, UpdateCommand cmd) {
            UpdateEvent updateEvent = new UpdateEvent(cmd.getNewState());
            eventHandler().apply(state, updateEvent);
            List<Event> persistEvents = Lists.newArrayList();
            if (!RemoteCache.getContext().isEmpty()) {
                persistEvents.add(new CacheInvoke(RemoteCache.getContext()));
            }
            persistEvents.add(updateEvent);
            return Effect().persist(persistEvents).thenRun(newState -> RemoteCache.remove()).thenReply(cmd.getReply(), newState -> newState);
        }


        @Override
        public EventHandler<String, Event> eventHandler() {
            return newEventHandlerBuilder().forAnyState().onEvent(UpdateEvent.class, this::updateState).onEvent(CacheInvoke.class, this::cacheInvoke).build();
        }

        private String cacheInvoke(String oldState, CacheInvoke evt) {
            RemoteCache.setContext(evt.getCacheMap());
            return oldState;
        }

        private String updateState(String oldState, UpdateEvent evt) {
            String newState = oldState;
            if (Objects.isNull(newState) || "".equals(newState)) {
                newState = RemoteCache.invoke("updateState", () -> remoteService.rpcState());
            }
            return newState.concat("@").concat(evt.getUpdateState());
        }
    }

    static class RemoteCache {

        private static ThreadLocal<Map<String, Object>> context = new ThreadLocal<>();

        public static void setContext(Map<String, Object> context) {
            getContext().putAll(context);
        }

        public static void remove() {
            context.remove();
        }

        public static Map<String, Object> getContext() {
            if (context.get() == null) {
                context.set(new HashMap<>());
            }
            return context.get();
        }

        public static <T> T getValue(String key) {
            Map<String, Object> context = getContext();
            Object o = context.get(key);
            if (Objects.isNull(o)) {
                return null;
            }
            return (T) o;
        }

        public static void setValue(String key, Object o) {
            Map<String, Object> context1 = getContext();
            context1.put(key, o);
        }


        public static <T> T invoke(String key, Callable<T> callable) {
            T value = getValue(key);
            if (Objects.nonNull(value)) {
                return value;
            }
            T call = null;
            try {
                call = callable.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            setValue(key, call);
            return call;
        }


    }
}
