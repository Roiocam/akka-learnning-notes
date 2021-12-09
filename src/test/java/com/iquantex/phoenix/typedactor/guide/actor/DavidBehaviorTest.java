package com.iquantex.phoenix.typedactor.guide.actor;

import com.iquantex.phoenix.typedactor.guide.actor.DavidBehavior.SayHello;
import com.iquantex.phoenix.typedactor.guide.protocol.HelloResponse;
import com.iquantex.phoenix.typedactor.guide.protocol.Message;
import com.iquantex.phoenix.typedactor.guide.system.TwoActorTellSystemActor;

import com.typesafe.config.ConfigFactory;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Behavior 的一些测试用例 */
public class DavidBehaviorTest {

    /**
     * 该测试演示了 {@link DavidBehavior} 接收并处理消息的能力，该测试将 {@link TwoActorTellSystemActor}
     * 作为用户守护Actor，并且创建了两个子Actor {@link DavidBehavior}
     *
     * <p>1. 演示了 {@link DavidBehavior#responseHello(SayHello)} ，该Actor接收 {@link SayHello} 并回复给发送者
     * {@link com.iquantex.phoenix.typedactor.guide.protocol.HelloResponse}
     *
     * <p>2. 演示了改变Actor行为的能力,Actor接受两次 {@link
     * com.iquantex.phoenix.typedactor.guide.protocol.HelloResponse} 之后，不会再处理该信息，这是由 {@link
     * DavidBehavior#wontHandleAnyMore(HelloResponse)} 的逻辑中定义，用了 {@link Behaviors#empty()} 实现不处理
     *
     * @throws InterruptedException
     */
    @Test
    public void actor_communicate_and_behavior_case() throws InterruptedException {
        ActorSystem<Message> actorSystem =
                ActorSystem.create(
                        TwoActorTellSystemActor.create(),
                        "test",
                        ConfigFactory.load("reference-simple.conf"));
        // 等待 System 执行任务
        Thread.sleep(100);
    }
}
