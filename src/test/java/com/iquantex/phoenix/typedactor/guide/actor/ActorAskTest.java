package com.iquantex.phoenix.typedactor.guide.actor;

import akka.actor.typed.RecipientRef;
import akka.japi.function.Function;
import akka.japi.function.Function2;
import com.iquantex.phoenix.typedactor.guide.protocol.Message;
import com.iquantex.phoenix.typedactor.guide.system.CreateAndAskSystemActor;
import com.iquantex.phoenix.typedactor.guide.system.CreateAndAskSystemActor.AskChild;

import com.typesafe.config.ConfigFactory;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * 该测试用例演示了 TypedActor 的请求/响应模型 {@link akka.actor.typed.javadsl.AskPattern}
 *
 * @author AndyChen
 */
public class ActorAskTest {

    /**
     * 该测试用例演示了 Actor.ask 的API，在 {@link CreateAndAskSystemActor} 被创建时，会创建一个子Actor {@link
     * DavidBehavior}，在子Actor启动后，父 Actor 向子Actor 发送 {@link com.iquantex.phoenix.typedactor.guide.actor.DavidBehavior.SayHello}
     * 消息
     *
     * @throws InterruptedException
     */
    @Test
    public void create_and_ask_case() throws InterruptedException {
        ActorSystem<Message> actorSystem =
            ActorSystem.create(
                CreateAndAskSystemActor.create(),
                "test",
                ConfigFactory.load("reference-simple.conf"));
        Thread.sleep(100);
    }

    /**
     * 该测试用例演示了 {@link akka.actor.typed.javadsl.ActorContext#pipeToSelf(CompletionStage, Function2)}
     * 的能力，以及 Actor 接受命令后向子Actor发起{@link akka.actor.typed.javadsl.ActorContext#ask(Class, RecipientRef, Duration, Function, Function2)}的能力
     *
     * @throws InterruptedException
     */
    @Test
    public void send_askChild_case() throws InterruptedException {
        ActorSystem<Message> actorSystem =
            ActorSystem.create(
                Behaviors.setup(
                    ctx -> {
                        // 1. 创建 CreateAndAskSystemActor
                        ActorRef<Message> askChildActor =
                            ctx.spawn(CreateAndAskSystemActor.create(), "askChild");
                        // 2. 发起 askChild，向其请求自己的子Actor David
                        askChildActor.tell(new AskChild());
                        return Behaviors.empty(); // 创建守护Actor
                    }),
                "test",
                ConfigFactory.load("reference-simple.conf"));
        Thread.sleep(500);
    }
}
