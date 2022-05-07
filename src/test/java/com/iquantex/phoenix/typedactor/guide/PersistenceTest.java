package com.iquantex.phoenix.typedactor.guide;

import akka.Done;
import akka.NotUsed;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import akka.japi.Pair;
import akka.persistence.jdbc.query.javadsl.JdbcReadJournal;
import akka.persistence.jdbc.testkit.javadsl.SchemaUtils;
import akka.persistence.query.EventEnvelope;
import akka.persistence.query.PersistenceQuery;
import akka.persistence.query.javadsl.ReadJournal;
import akka.persistence.testkit.SnapshotMeta;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import akka.persistence.testkit.javadsl.SnapshotTestKit;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.RecoveryCompleted;
import akka.persistence.typed.javadsl.*;
import akka.stream.javadsl.Source;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.typesafe.config.ConfigFactory;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class PersistenceTest {

    @ClassRule
    private TestKitJunitResource testkit = new TestKitJunitResource(EventSourcedBehaviorTestKit.config().withFallback(ConfigFactory.parseString("akka.loglevel=DEBUG")));

    private RemoteService remoteService = new RemoteService();

    private EventSourcedBehaviorTestKit<Command, Event, String> eventSouredTestkit = EventSourcedBehaviorTestKit.create(testkit.system(), new PersistActor(PersistenceId.ofUniqueId("persistActor_01"), remoteService));
    private SnapshotTestKit snapshotTestKit = eventSouredTestkit.snapshotTestKit().get();

    @Before
    public void beforeEach() {
        eventSouredTestkit.clear();
    }

    @Test
    public void valid_reset_invoke_case() {
        EventSourcedBehaviorTestKit.CommandResultWithReply<Command, Event, String, String> result = eventSouredTestkit.runCommand(actorRef -> new UpdateCommand("asd", actorRef));
        // 事件
        List<Event> events = result.events();
        // 状态
        String state = result.state();

        log.info("更新后状态={}", state);
        events.stream().forEach(e -> log.info("更新事件={}", JSON.toJSONString(e)));
        log.info("============= 重启 Actor");
        EventSourcedBehaviorTestKit.RestartResult<String> restart = eventSouredTestkit.restart();
        log.info("重启后状态={}", restart.state());
        // 更新多次
        eventSouredTestkit.<String>runCommand(actorRef -> new UpdateCommand("def", actorRef));
        String snapshotValue = snapshotTestKit.expectNextPersistedClass("persistActor_01", String.class);
        log.info("观察快照={}", snapshotValue);
        eventSouredTestkit.<String>runCommand(actorRef -> new UpdateCommand("ghj", actorRef));
        EventSourcedBehaviorTestKit.CommandResultWithReply<Command, Event, String, String> snapshot = eventSouredTestkit.<String>runCommand(actorRef -> new UpdateCommand("klm", actorRef));
        List<Event> events1 = snapshot.events();
        log.info("更新后状态={}", snapshot.state());
        events1.stream().forEach(e -> log.info("更新事件={}", JSON.toJSONString(e)));

        log.info("============= 重启 Actor");
        EventSourcedBehaviorTestKit.RestartResult<String> restart2 = eventSouredTestkit.restart();
        log.info("重启后状态={}", restart2.state());
    }

    /**
     * 验证快照吞掉缓存事件
     * [event1,event2,cacheEvent ] [event3,event4,event5]
     */
    @Test
    public void valid_cache_with_snapshot() {

        // rpcState@1
        // cache + event1
        EventSourcedBehaviorTestKit.CommandResultWithReply<Command, Event, String, String> result1 = eventSouredTestkit.<String>runCommand(actorRef -> new UpdateCommand("1", actorRef));
        log.info("更新后状态={} || {}", result1.state(), eventSouredTestkit.getState());
        List<Object> eventList = eventSouredTestkit.persistenceTestKit().persistedInStorage("persistActor_01");
        log.info("事件集合={} ", eventList);
        log.info("二次更新");
        EventSourcedBehaviorTestKit.CommandResultWithReply<Command, Event, String, String> result = eventSouredTestkit.<String>runCommand(actorRef -> new UpdateCommand("2", actorRef));
        // 事件
        List<Event> events = result.events();
        log.info("更新后状态={} || {}", result.state(), eventSouredTestkit.getState());
        List<Object> eventList2 = eventSouredTestkit.persistenceTestKit().persistedInStorage("persistActor_01");
        log.info("事件集合={} ", eventList2);
        events.stream().forEach(e -> log.info("更新事件={}", JSON.toJSONString(e)));
        List<Pair<SnapshotMeta, Object>> snapshotValue = snapshotTestKit.persistedInStorage("persistActor_01");
        log.info("快照集合={} ", snapshotValue);

        System.out.println("cxv");
        EventSourcedBehaviorTestKit.CommandResultWithReply<Command, Event, String, String> result3 = eventSouredTestkit.<String>runCommand(actorRef -> new UpdateCommand("3", actorRef));

        System.out.println("asd");

        EventSourcedBehaviorTestKit.RestartResult<String> restart = eventSouredTestkit.restart();
        log.info("重启结果={} ", restart.state());
        List<Object> eventList3 = eventSouredTestkit.persistenceTestKit().persistedInStorage("persistActor_01");
        log.info("事件集合={} ", eventList3);


    }

    @Test
    public void test_with_integration() throws InterruptedException {

        AtomicReference<ActorRef<Command>> ref = new AtomicReference<>();

        ActorSystem<Object> name = ActorSystem.create(Behaviors.setup(context -> {

                    ref.set(context.spawn(
                            Behaviors.setup(ctx -> new PersistActor(PersistenceId.ofUniqueId("persistActor_01"), remoteService)),
                            "myName"
                    ));

                    return Behaviors.empty();
                }),
                "name",
                ConfigFactory.load("reference-persistence.conf")
        );
        SchemaUtils.createIfNotExists(name);


        CompletionStage<String> ask = AskPattern.ask(
                ref.get(),
                actorRef -> new UpdateCommand("2", actorRef),
                Duration.ofSeconds(5),
                testkit.scheduler()
        );

        ask.toCompletableFuture().join();
        CompletionStage<String> ask2 = AskPattern.ask(
                ref.get(),
                actorRef -> new UpdateCommand("3", actorRef),
                Duration.ofSeconds(5),
                testkit.scheduler()
        );
        ask2.toCompletableFuture().join();

        CompletionStage<String> ask3 = AskPattern.ask(
                ref.get(),
                actorRef -> new UpdateCommand("4", actorRef),
                Duration.ofSeconds(5),
                testkit.scheduler()
        );
        ask3.toCompletableFuture().join();

        Thread.sleep(1000);

        JdbcReadJournal readJournalFor = PersistenceQuery.get(name).getReadJournalFor(
                JdbcReadJournal.class, "jdbc-read-journal"
        );

        Source<EventEnvelope, NotUsed> source = readJournalFor.eventsByPersistenceId("persistActor_01", 0, Long.MAX_VALUE);

        CompletionStage<Done> doneCompletionStage = source.runForeach(event -> {
            System.out.println(event);
        }, name);

       doneCompletionStage.toCompletableFuture().join();


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

    /**
     * 模拟IO
     */
    static class RemoteService {

        public String rpcState() {
            log.info("IO调用");
            return "rpcState";
        }
    }

    /**
     * 模拟框架代码.
     */
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
        public RetentionCriteria retentionCriteria() {
            return RetentionCriteria.snapshotEvery(3, 1).withDeleteEventsOnSnapshot();
        }

        @Override
        public SignalHandler<String> signalHandler() {
            return newSignalHandlerBuilder()
                    .onSignal(RecoveryCompleted.class, this::removeCache)
                    .build();
        }

        /**
         * 溯源成功时, 清除缓存
         *
         * @param s
         * @param signal
         */
        private void removeCache(String s, RecoveryCompleted signal) {
            log.info("恢复成功={}, 清除缓存", signal);
            RemoteCache.remove();
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
            if (Objects.isNull(newState) || "".equals(newState) || "rpcState@1".equals(newState)) {
                log.info("需要IO调用");
                if ("rpcState@1".equals(newState)) {
                    log.info("连接");
                    newState = newState.concat("@").concat(RemoteCache.invoke("updateState", () -> remoteService.rpcState()));
                } else {
                    newState = RemoteCache.invoke("updateState", () -> remoteService.rpcState());
                }
            }
            return newState.concat("@").concat(evt.getUpdateState());
        }
    }

    /**
     * 用于缓存 IO 操作, 或者设置 IO 缓存
     */
    static class RemoteCache {

        private static ThreadLocal<Map<String, Object>> context = new ThreadLocal<>();

        public static void setContext(Map<String, Object> context) {
            log.info("设置缓存");
            getContext().putAll(context);
        }

        public static void remove() {
            log.info("清除缓存");
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
                log.info("调用 invoke => 存在缓存={}", value);
                return value;
            }
            T call = null;
            try {
                log.info("调用 invoke => 获取新值={}", call);
                call = callable.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            setValue(key, call);
            return call;
        }

    }
}
