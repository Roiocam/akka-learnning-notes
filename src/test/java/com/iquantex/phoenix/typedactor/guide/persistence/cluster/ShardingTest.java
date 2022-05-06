package com.iquantex.phoenix.typedactor.guide.persistence.cluster;

import com.iquantex.phoenix.typedactor.guide.persistence.BookBehavior;
import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand;
import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand.AddBook;
import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand.GetBook;
import com.iquantex.phoenix.typedactor.guide.persistence.BookState.Book;

import com.typesafe.config.ConfigFactory;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.ClusterShardingSettings;
import akka.cluster.sharding.typed.HashCodeMessageExtractor;
import akka.cluster.sharding.typed.ShardingEnvelope;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 该测试用例演示了一个集群示例，该集群只有一个节点，该测试用例在集群中创建了分片实体"Book" {@link BookBehavior} 其分片策略为 1
 *
 * @author AndyChen
 */
@Slf4j
public class ShardingTest {

    private static Book add;
    private static Book update;
    private static Book recovery;
    private static ActorSystem<StatusReply> system;
    private static ActorRef<StatusReply> replyActorRef;
    private static ActorRef<ShardingEnvelope<BookCommand>> shardRegion;
    private static ClusterSharding sharding;
    private static EntityTypeKey<BookCommand> typeKey;

    @BeforeAll
    public static void setUp() {
        add = new Book(UUID.randomUUID().toString(), "Java In Action", "Markting", 109L);
        update = new Book(add.getUuid(), "Java In Action", "Marting", 99L);
        recovery =
                new Book(UUID.randomUUID().toString(), "Domain Driven Design", "Chris Evans", 199L);

        // 基于用户守护Actor创建System
        ActorSystem<StatusReply> actorSystem =
                ActorSystem.create(
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
        // 集群分片配置
        ClusterShardingSettings settings = ClusterShardingSettings.create(system);
        // 首先需要创建实体类型的Key，其name = "Book" 必须是唯一的。
        typeKey = EntityTypeKey.create(BookCommand.class, "Book");
        // 然后定义实体的创建，相关设置现在都在这里配置
        Entity<BookCommand, ShardingEnvelope<BookCommand>> entity =
                Entity.of(
                                typeKey, // 实体类型Key
                                ctx ->
                                        BookBehavior.create(
                                                PersistenceId.of(
                                                        ctx.getEntityTypeKey().name(),
                                                        ctx.getEntityId()))) // 实体工厂
                        .withSettings(settings) // 配置分片 setting
                        .withMessageExtractor(
                                new HashCodeMessageExtractor<>(4)); // 设置了1个分片数量,通过hashcode 分片
        // 最后在集群分片中初始化实体
        sharding = ClusterSharding.get(system);
        shardRegion = sharding.init(entity);
    }

    /** 等待事件溯源,每个测试用例都会执行 */
    @BeforeEach
    public void waitingForRecovery() throws InterruptedException {
        Thread.sleep(200);
    }

    @Test
    public void entityRef_sendMessage_case()
            throws ExecutionException, InterruptedException, TimeoutException {

        // 获取具体的 Sharding Actor
        EntityRef<BookCommand> entityRef = sharding.entityRefFor(typeKey, "Book-0");
        // 发送信息
        entityRef.tell(
                new AddBook(
                        replyActorRef,
                        add.getUuid(),
                        add.getTitle(),
                        add.getAuthor(),
                        add.getPrice()));

        CompletionStage<StatusReply> ask =
                entityRef.ask(
                        replyTo -> new GetBook(replyTo, add.getUuid()), Duration.ofMillis(1000));

        StatusReply res = ask.toCompletableFuture().get(1100, TimeUnit.MILLISECONDS);
        log.info("结果={}", JSON.toJSONString(res.getValue()));
        Book value = (Book) res.getValue();
        assertEquals(add.getUuid(), value.getUuid());
        assertEquals(JSON.toJSONString(add), JSON.toJSONString(value));
    }

    @Test
    public void shardRegion_sendMessage_case()
            throws ExecutionException, InterruptedException, TimeoutException {

        shardRegion.tell(
                new ShardingEnvelope<>(
                        "Book-1",
                        new AddBook(
                                replyActorRef,
                                add.getUuid(),
                                add.getTitle(),
                                add.getAuthor(),
                                add.getPrice())));

        CompletionStage<StatusReply> ask =
                AskPattern.ask(
                        shardRegion,
                        replyTo ->
                                new ShardingEnvelope<>(
                                        "Book-1", new GetBook(replyTo, add.getUuid())),
                        Duration.ofMillis(1000),
                        system.scheduler());

        StatusReply res = ask.toCompletableFuture().get(1100, TimeUnit.MILLISECONDS);
        log.info("结果={}", JSON.toJSONString(res.getValue()));
        Book value = (Book) res.getValue();
        assertEquals(add.getUuid(), value.getUuid());
        assertEquals(JSON.toJSONString(add), JSON.toJSONString(value));
    }

    @Test
    public void wrongEntityId_case()
            throws ExecutionException, InterruptedException, TimeoutException {
        shardRegion.tell(
                new ShardingEnvelope<>(
                        "Book-2",
                        new AddBook(
                                replyActorRef,
                                add.getUuid(),
                                add.getTitle(),
                                add.getAuthor(),
                                add.getPrice())));

        // 等待持久化
        Thread.sleep(1000);

        CompletionStage<StatusReply> failAsk =
                AskPattern.ask(
                        shardRegion,
                        replyTo ->
                                new ShardingEnvelope<>(
                                        "Book-3", new GetBook(replyTo, add.getUuid())),
                        Duration.ofMillis(1000),
                        system.scheduler());

        StatusReply res = failAsk.toCompletableFuture().get(1100, TimeUnit.MILLISECONDS);
        assertTrue(res.isError());
    }
}
