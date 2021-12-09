package com.iquantex.phoenix.typedactor.guide.system;

import com.iquantex.phoenix.typedactor.guide.protocol.Message;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import akka.actor.Address;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ActorSystem 的测试用例
 *
 * @author AndyChen
 */
public class ActorSystemTest {

    /**
     * 类似于 ActorSystemHelper，这里依赖于test.conf创建了一个名为test的{@link ActorSystem} 不同于 Classic Actor, Typed
     * Actor 的 ActorSystem API 强制要求用户定义守护 Actor (Guardian)
     *
     * <p>新版的方法是：{@link ActorSystem#create(Behavior, String)}
     *
     * <p>传统的方法是：{@link akka.actor.ActorSystem#create(String)}
     */
    @Test
    public void empty_system_case() {
        ActorSystem<Object> actorSystem =
                ActorSystem.create(
                        Behaviors.empty(), // 创建了一个无行为的守护Actor
                        "empty" // SystemName
                        );
        actorSystem.log().info(actorSystem.printTree());
    }

    /**
     * 创建没有任何行为的守护Actor，并加载配置文件启动System
     *
     * <p>新版的方法是：{@link ActorSystem#create(Behavior, String, Config)}
     *
     * <p>传统的方法是：{@link akka.actor.ActorSystem#create(String, Config)}
     */
    @Test
    public void fromConfig_system_case() {
        ActorSystem<Object> actorSystem =
                ActorSystem.create(
                        Behaviors.empty(), "test", ConfigFactory.load("reference-test.conf") // 加载配置
                        );
        Address address = actorSystem.address();
        assertEquals("akka://test@127.0.0.1:2551", address.toString());
    }

    /**
     * 该Actor演示如何在System下创建两个子Actor，子Actor在用户守护Actor下创建
     *
     * @throws InterruptedException
     */
    @Test
    public void twoChild_system_case() throws InterruptedException {
        ActorSystem<Message> actorSystem =
                ActorSystem.create(
                        TwoChildSystemActor.create(),
                        "test",
                        ConfigFactory.load("reference-simple.conf"));
        actorSystem.log().info(actorSystem.printTree());
        // 等待 System 创建Child
        actorSystem.log().info("-----------------等待创建子Actor");
        Thread.sleep(100);
        actorSystem.log().info(actorSystem.printTree());
    }
}
