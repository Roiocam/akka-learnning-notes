package com.iquantex.phoenix.typedactor.guide.actor;

import com.iquantex.phoenix.typedactor.guide.actor.DavidBehavior.SayHello;
import com.iquantex.phoenix.typedactor.guide.constant.ServiceKeys;
import com.iquantex.phoenix.typedactor.guide.protocol.HelloResponse;
import com.iquantex.phoenix.typedactor.guide.protocol.Message;
import com.iquantex.phoenix.typedactor.guide.system.RegisterToReceptionistSystemActor;
import com.iquantex.phoenix.typedactor.guide.system.RegisterToReceptionistSystemActor.GetChild;

import com.typesafe.config.ConfigFactory;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.Receptionist.Command;
import akka.actor.typed.receptionist.Receptionist.Listing;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 该案例用于演示本机如何在 Actor 外部获取 {@link ActorRef}, 如 Web 程序中 Controller 向 Actor 发送请求, 首先要拿到 {@link
 * ActorRef}, 但是新版的Actor创建在{@link ActorSystem} 内部
 *
 * @author AndyChen
 */
public class GetActorRefTest {

    /**
     * 该测试演示如何通过 {@link Receptionist} （一种特殊的Actor）在 ActorSystem 外部拿到 用户Actor的Ref
     *
     * <p>这种方法只能找到主动在 {@link Receptionist} 中通过 {@link akka.actor.typed.receptionist.ServiceKey}
     * 注册的Actor
     *
     * @throws InterruptedException
     * @throws ExecutionException
     * @throws TimeoutException
     */
    @Test
    public void get_actorRef_from_receptionist_case()
            throws InterruptedException, ExecutionException, TimeoutException {
        // 1. 将 RegisterToReceptionistSystemActor 作为用户守护Actor,该守护Actor会创建DavidActor
        ActorSystem<Message> actorSystem =
                ActorSystem.create(
                        RegisterToReceptionistSystemActor.create(),
                        "test",
                        ConfigFactory.load("reference-simple.conf"));
        // 2. 拿到  Receptionist Actor
        ActorRef<Command> receptionist = actorSystem.receptionist();
        // 3. 外部向 Receptionist Actor 请求 David
        Thread.sleep(100); // 等待 System 加载完成
        CompletionStage<Listing> result =
                AskPattern.ask(
                        receptionist,
                        replyTo -> Receptionist.find(ServiceKeys.DAVID_KEY, replyTo),
                        Duration.ofMillis(100),
                        actorSystem.scheduler());
        // 4. 筛选结果,拿任意结果
        ActorRef<Message> actorRef =
                result.thenApply(
                                reply ->
                                        Optional.ofNullable(reply)
                                                .filter(
                                                        res ->
                                                                res
                                                                        instanceof
                                                                        Listing) // 判断是否 Listing
                                                .map(
                                                        res ->
                                                                res.getServiceInstances(
                                                                        ServiceKeys.DAVID_KEY))
                                                .map(refs -> refs.stream().findAny().get())
                                                .orElse(null))
                        .toCompletableFuture()
                        .get(100, TimeUnit.MILLISECONDS);
        assertNotNull(actorRef);
        actorSystem.log().info("获取到了DavidActor address={}", actorRef.path());
    }

    /**
     * 该案例演示了如何在Actor内部拿到自己的Child {@link ActorRef}. 这里用到的是 {@link ActorContext#getChildren()},
     * 也可以通过特定的 name找到相应的Actor
     *
     * @throws InterruptedException
     */
    @Test
    public void get_actorRef_from_getChild_case() throws InterruptedException {
        ActorSystem<Message> actorSystem =
                ActorSystem.create(
                        Behaviors.setup(
                                ctx -> {
                                    // 1. 创建 RegisterToReceptionistSystemActor
                                    ActorRef<Message> actorRef =
                                            ctx.spawn(
                                                    RegisterToReceptionistSystemActor.create(),
                                                    "parent");
                                    // 2. 向 RegisterToReceptionistSystemActor
                                    // 发送获取ChildActor的请求，并观察打印日志
                                    actorRef.tell(new GetChild());
                                    return Behaviors.empty();
                                }),
                        "test",
                        ConfigFactory.load("reference-simple.conf"));
        Thread.sleep(100);
    }

    /**
     * 该案例演示了如何在ActorSystem 外部向 Actor 发起请求.这里用的是 {@link AskPattern} 实现。类似于 PhoenixClient
     * 的功能,但是无需借助Kafka
     *
     * @throws InterruptedException
     * @throws ExecutionException
     * @throws TimeoutException
     */
    @Test
    public void outside_actor_ask_case()
            throws InterruptedException, ExecutionException, TimeoutException {
        // 1. 将 RegisterToReceptionistSystemActor 作为用户守护Actor,该守护Actor会创建DavidActor
        ActorSystem<Message> actorSystem =
                ActorSystem.create(
                        RegisterToReceptionistSystemActor.create(),
                        "test",
                        ConfigFactory.load("reference-simple.conf"));
        // 2. 拿到  Receptionist Actor
        ActorRef<Command> receptionist = actorSystem.receptionist();
        // 3. 外部向 Receptionist Actor 请求 David
        Thread.sleep(100); // 等待 System 加载完成
        CompletionStage<Listing> result =
                AskPattern.ask(
                        receptionist,
                        replyTo -> Receptionist.find(ServiceKeys.DAVID_KEY, replyTo),
                        Duration.ofMillis(100),
                        actorSystem.scheduler());
        // 4. 筛选结果,拿任意结果
        ActorRef<Message> actorRef =
                result.thenApply(
                                reply ->
                                        Optional.ofNullable(reply)
                                                .filter(
                                                        res ->
                                                                res
                                                                        instanceof
                                                                        Listing) // 判断是否 Listing
                                                .map(
                                                        res ->
                                                                res.getServiceInstances(
                                                                        ServiceKeys.DAVID_KEY))
                                                .map(refs -> refs.stream().findAny().get())
                                                .orElse(null))
                        .toCompletableFuture()
                        .get(100, TimeUnit.MILLISECONDS);
        assertNotNull(actorRef);
        // 5. 外部向 actorRef 发起ask
        CompletionStage<Message> completionStage =
                AskPattern.ask(
                        actorRef,
                        anonymousActorRef -> new SayHello(anonymousActorRef),
                        Duration.ofMillis(100),
                        actorSystem.scheduler());
        // 6. 获取异步结果, 并且打印.
        Message message = completionStage.toCompletableFuture().get(100, TimeUnit.MILLISECONDS);

        HelloResponse helloResponse = (HelloResponse) message;
        actorSystem
                .log()
                .info(
                        "Actor外部请求Actor {}, Response={}",
                        helloResponse.getReplyTo().path(),
                        helloResponse.getResponse());
        Thread.sleep(100);
    }
}
