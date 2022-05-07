package com.iquantex.phoenix.typedactor.guide;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.ActorRef;
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.RecoveryCompleted;
import akka.persistence.typed.javadsl.*;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

@Slf4j
public class PersistenceTest {

    @ClassRule
    private TestKitJunitResource testkit = new TestKitJunitResource(EventSourcedBehaviorTestKit.config());

    private RemoteService remoteService = new RemoteService();

    private EventSourcedBehaviorTestKit<Command, Event, String> eventSouredTestkit = EventSourcedBehaviorTestKit.create(testkit.system(), new PersistActor(PersistenceId.ofUniqueId("persistActor_01"), remoteService));


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
        public SignalHandler<String> signalHandler() {
            return newSignalHandlerBuilder()
                    .onSignal(RecoveryCompleted.class, this::removeCache)
                    .build();
        }

        /**
         * 溯源成功时, 清除缓存
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
            if (Objects.isNull(newState) || "".equals(newState)) {
                log.info("需要IO调用");
                newState = RemoteCache.invoke("updateState", () -> remoteService.rpcState());
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
