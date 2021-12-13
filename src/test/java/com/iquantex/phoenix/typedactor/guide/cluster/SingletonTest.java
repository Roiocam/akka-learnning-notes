package com.iquantex.phoenix.typedactor.guide.cluster;

import com.iquantex.phoenix.typedactor.guide.persistence.BookBehavior;
import com.iquantex.phoenix.typedactor.guide.persistence.BookBehaviorTest;
import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand;
import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand.AddBook;
import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand.GetBook;
import com.iquantex.phoenix.typedactor.guide.persistence.BookState.Book;

import com.typesafe.config.ConfigFactory;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.typed.ClusterSingleton;
import akka.cluster.typed.ClusterSingletonSettings;
import akka.cluster.typed.SingletonActor;
import akka.pattern.StatusReply;
import akka.persistence.jdbc.testkit.javadsl.SchemaUtils;
import akka.persistence.typed.PersistenceId;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 该测试用例演示了集群单例Actor如何工作.
 *
 * <p>1. 首先我们需要使用 {@link SingletonActor#of(Behavior, String)} 创建封装后的单例 Actor
 *
 * <p>2. 然后通过 {@link ClusterSingleton#get(ActorSystem)} 获取集群单例对象
 *
 * <p>3. 最后在 {@link ClusterSingleton#init(SingletonActor)} 中初始化集群单例 Actor 的创建方式 相比与经典
 * Actor，在Typed中，集群对象的创建更加简单，无需 {@link akka.cluster.singleton.ClusterSingletonProxy}
 *
 * @author AndyChen
 */
@Slf4j
public class SingletonTest {

    private static Book add;
    private static Book update;
    private static Book recovery;
    private static ActorSystem<StatusReply> system;
    private static ActorRef<StatusReply> replyActorRef;
    private static ClusterSingleton singleton;
    private static ActorRef<BookCommand> bookActorRef;

    @BeforeAll
    public static void setUp() {
        add = new Book(UUID.randomUUID().toString(), "Java In Action", "Markting", 109L);
        update = new Book(add.getUuid(), "Java In Action", "Marting", 99L);
        recovery =
                new Book(UUID.randomUUID().toString(), "Domain Driven Design", "Chris Evans", 199L);

        // 基于用户守护Actor创建System
        ActorSystem<StatusReply> actorSystem =
                ActorSystem.<StatusReply>create(
                        Behaviors.setup(
                                ctx -> {
                                    replyActorRef = ctx.getSelf();
                                    return Behaviors.empty();
                                }),
                        "test",
                        ConfigFactory.load("reference-cluster.conf") // 加载配置
                        );
        // 初始化数据库Schema
        SchemaUtils.createIfNotExists(actorSystem);
        system = actorSystem;
        // 集群单例配置
        ClusterSingletonSettings settings = ClusterSingletonSettings.create(system);
        // 定义集群单例 Actor
        SingletonActor<BookCommand> bookSingleton =
                SingletonActor.of(
                                BookBehavior.create(
                                        PersistenceId.ofUniqueId("book-01")), // 创建 Actor
                                "bookSingleton") // 集群单例全局唯一的n ame
                        .withSettings(settings); // 配置
        // 初始化集群单例对象
        ClusterSingleton singleton = ClusterSingleton.get(system);
        // 最后在集群单例中初始化单例Actor
        bookActorRef = singleton.init(bookSingleton);
    }

    /** 等待事件溯源,每个测试用例都会执行 */
    @BeforeEach
    public void waitingForRecovery() throws InterruptedException {
        Thread.sleep(200);
    }

    /** 该测试用例与 {@link BookBehaviorTest#addBook_andGet_case()} 一致 */
    @Test
    public void addBook_andGet_case()
            throws InterruptedException, ExecutionException, TimeoutException {
        bookActorRef.tell(
                new AddBook(
                        replyActorRef,
                        add.getUuid(),
                        add.getTitle(),
                        add.getAuthor(),
                        add.getPrice()));

        CompletionStage<StatusReply> ask =
                AskPattern.ask(
                        bookActorRef,
                        replyTo -> new GetBook(replyTo, add.getUuid()),
                        Duration.ofMillis(1000),
                        system.scheduler());
        StatusReply res = ask.toCompletableFuture().get(1100, TimeUnit.MILLISECONDS);
        log.info("结果={}", JSON.toJSONString(res.getValue()));
        Book value = (Book) res.getValue();
        assertEquals(add.getUuid(), value.getUuid());
        assertEquals(JSON.toJSONString(add), JSON.toJSONString(value));
    }
}
