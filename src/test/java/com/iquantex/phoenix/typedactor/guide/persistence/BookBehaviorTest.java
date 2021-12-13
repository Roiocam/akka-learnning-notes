package com.iquantex.phoenix.typedactor.guide.persistence;

import com.iquantex.phoenix.typedactor.guide.constant.ServiceKeys;
import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand.AddBook;
import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand.ClearAll;
import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand.GetAllBook;
import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand.GetBook;
import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand.RemoveBook;
import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand.UpdateBook;
import com.iquantex.phoenix.typedactor.guide.persistence.BookState.Book;

import com.typesafe.config.ConfigFactory;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.Receptionist.Command;
import akka.actor.typed.receptionist.Receptionist.Listing;
import akka.pattern.StatusReply;
import akka.persistence.jdbc.testkit.javadsl.SchemaUtils;
import akka.persistence.typed.PersistenceId;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 该测试用例演示了 {@link BookBehavior} ,实现了对 {@link Book} 的增删改查，以及事件溯源. 其持久化实现用的是 Actor 的 EventSoured
 * 持久化数据库为 h2 -> 在 reference-persistence.conf 中配置.
 */
@Slf4j
public class BookBehaviorTest {

    private static Book add;
    private static Book update;
    private static Book recovery;
    private static ActorRef<BookCommand> bookActorRef;
    private static ActorSystem<StatusReply> system;
    private static ActorRef<StatusReply> replyActorRef;
    private static Behavior<StatusReply> userGuardianActor;

    @BeforeAll
    public static void setUp() throws InterruptedException {
        add = new Book(UUID.randomUUID().toString(), "Java In Action", "Markting", 109L);
        update = new Book(add.getUuid(), "Java In Action", "Marting", 99L);
        recovery =
                new Book(UUID.randomUUID().toString(), "Domain Driven Design", "Chris Evans", 199L);

        // 用户守护Actor 创建工厂
        userGuardianActor =
                Behaviors.setup(
                        ctx -> {
                            ActorRef<BookCommand> bookActor =
                                    ctx.spawn(
                                            BookBehavior.create(
                                                    PersistenceId.ofUniqueId("bookActor")),
                                            "bookActor");
                            ctx.watch(bookActor);
                            // 注册到Receptionist
                            ctx.getSystem()
                                    .receptionist()
                                    .tell(Receptionist.register(ServiceKeys.BOOK_KEY, bookActor));
                            bookActorRef = bookActor;
                            replyActorRef = ctx.getSelf();
                            return Behaviors.empty();
                        });
        // 基于用户守护Actor创建System
        ActorSystem<StatusReply> actorSystem =
                ActorSystem.create(
                        userGuardianActor,
                        "test",
                        ConfigFactory.load("reference-persistence.conf") // 加载配置
                        );
        // 初始化数据库Schema
        SchemaUtils.createIfNotExists(actorSystem);
        system = actorSystem;
        // 清除旧数据
        bookActorRef.tell(new ClearAll(replyActorRef));
    }

    /** 等待事件溯源,每个测试用例都会执行 */
    @BeforeEach
    public void waitingForRecovery() throws InterruptedException {
        Thread.sleep(200);
    }

    /**
     * 测试用例演示了 EventSouredActor 接收 {@link AddBook} 命令，命令的效果是 {@link
     * com.iquantex.phoenix.typedactor.guide.persistence.BookEvent.BookAdded} 事件，该事件会改变Actor状态，添加
     * {@link Book} 到 {@link BookState}. 最后通过 {@link GetBook} 查看状态是否改变
     */
    @Test
    @Order(1)
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
                        Duration.ofMillis(200),
                        system.scheduler());
        StatusReply res = ask.toCompletableFuture().get(300, TimeUnit.MILLISECONDS);
        log.info("结果={}", JSON.toJSONString(res.getValue()));
        Book value = (Book) res.getValue();
        assertEquals(add.getUuid(), value.getUuid());
        assertEquals(JSON.toJSONString(add), JSON.toJSONString(value));
    }

    /**
     * 该测试演示了 {@link UpdateBook} 命令，该命令的效果是两个事件，分别是 {@link
     * com.iquantex.phoenix.typedactor.guide.persistence.BookEvent.BookRemoved} 和 {@link
     * com.iquantex.phoenix.typedactor.guide.persistence.BookEvent.BookAdded} 然后又添加了 {@link Book}
     * 并通过 {@link GetAllBook} 验证当前状态是否匹配
     */
    @Test
    @Order(2)
    public void update_and_GetAll_case()
            throws ExecutionException, InterruptedException, TimeoutException {
        bookActorRef.tell(
                new UpdateBook(
                        replyActorRef,
                        update.getUuid(),
                        update.getTitle(),
                        update.getAuthor(),
                        update.getPrice()));

        bookActorRef.tell(
                new AddBook(
                        replyActorRef,
                        recovery.getUuid(),
                        recovery.getTitle(),
                        recovery.getAuthor(),
                        recovery.getPrice()));

        CompletionStage<StatusReply> ask =
                AskPattern.ask(
                        bookActorRef,
                        replyTo -> new GetAllBook(replyTo),
                        Duration.ofMillis(500),
                        system.scheduler());
        StatusReply res = ask.toCompletableFuture().get(600, TimeUnit.MILLISECONDS);
        log.info("结果={}", JSON.toJSONString(res.getValue()));
        Map<String, Book> value = (Map<String, Book>) res.getValue();
        assertNotNull(value.get(add.getUuid()));
        assertEquals(2, value.size());
        assertEquals(JSON.toJSONString(update), JSON.toJSONString(value.get(update.getUuid())));
    }

    /**
     * 该测试用例演示了 {@link RemoveBook} 的效果 {@link
     * com.iquantex.phoenix.typedactor.guide.persistence.BookEvent.BookRemoved} 改变状态后，{@link
     * GetBook} 是否能产生不同的行为
     */
    @Test
    @Order(3)
    public void remove_and_NotFoundGet_case()
            throws ExecutionException, InterruptedException, TimeoutException {
        bookActorRef.tell(new RemoveBook(replyActorRef, add.getUuid()));

        CompletionStage<StatusReply> ask =
                AskPattern.ask(
                        bookActorRef,
                        replyTo -> new GetBook(replyTo, add.getUuid()),
                        Duration.ofMillis(100),
                        system.scheduler());
        StatusReply res = ask.toCompletableFuture().get(100, TimeUnit.MILLISECONDS);
        assertTrue(res.isError());
    }

    /** 该测试用例演示了 {@link ActorSystem} 停机后, 是否能通过 EventStore/Journal 中的事件正确回溯 Actor 状态 */
    @Test
    @Order(4)
    public void recovery_case() throws InterruptedException, ExecutionException, TimeoutException {
        // 重启system,验证事件溯源,观察日志是否包含 恢复完成
        system.terminate();
        ActorSystem<StatusReply> actorSystem =
                ActorSystem.create(
                        userGuardianActor,
                        "test",
                        ConfigFactory.load("reference-persistence.conf"));
        // 等待事件溯源
        Thread.sleep(1000);
        // 拿到原来的ActorRef
        ActorRef<Command> receptionist = actorSystem.receptionist();
        CompletionStage<Listing> result =
                AskPattern.ask(
                        receptionist,
                        replyTo -> Receptionist.find(ServiceKeys.BOOK_KEY, replyTo),
                        Duration.ofMillis(100),
                        actorSystem.scheduler());
        ActorRef<BookCommand> actorRef =
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
                                                                        ServiceKeys.BOOK_KEY))
                                                .map(refs -> refs.stream().findAny().get())
                                                .orElse(null))
                        .toCompletableFuture()
                        .get(100, TimeUnit.MILLISECONDS);
        assertNotNull(actorRef);
        // 请求 Actor, 获取 Book 是否正常回溯
        CompletionStage<StatusReply> ask =
                AskPattern.ask(
                        actorRef,
                        replyTo -> new GetBook(replyTo, recovery.getUuid()),
                        Duration.ofMillis(100),
                        actorSystem.scheduler());
        StatusReply res = ask.toCompletableFuture().get(100, TimeUnit.MILLISECONDS);
        log.info("结果={}", JSON.toJSONString(res.getValue()));
        Book value = (Book) res.getValue();
        assertEquals(recovery.getUuid(), value.getUuid());
        assertEquals(JSON.toJSONString(recovery), JSON.toJSONString(value));
    }
}
