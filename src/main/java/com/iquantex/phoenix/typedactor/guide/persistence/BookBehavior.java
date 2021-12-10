package com.iquantex.phoenix.typedactor.guide.persistence;

import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand.AddBook;
import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand.ClearAll;
import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand.GetAllBook;
import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand.GetBook;
import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand.RemoveBook;
import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand.UpdateBook;
import com.iquantex.phoenix.typedactor.guide.persistence.BookEvent.AllBookClear;
import com.iquantex.phoenix.typedactor.guide.persistence.BookEvent.BookAdded;
import com.iquantex.phoenix.typedactor.guide.persistence.BookEvent.BookRemoved;
import com.iquantex.phoenix.typedactor.guide.persistence.BookState.Book;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.japi.function.Function;
import akka.pattern.StatusReply;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.RecoveryCompleted;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.Effect;
import akka.persistence.typed.javadsl.EffectFactories;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import akka.persistence.typed.javadsl.SignalHandler;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

/**
 * 持久化 Actor 演示类, 持久化的 Actor 需要继承 {@link EventSourcedBehavior} 该抽象类包含了三个泛型.分别是：
 *
 * <p>command : Actor 能够接受的命令消息
 *
 * <p>event: command 命令产生的效果/ 产生的事件，event 会被持久化, event会改变Actor状态
 *
 * <p>state: 表示程序当前的状态，用于存储快照，在事件溯源时加快速度。因为 Behavior 本身只是表达行为，所以需要单独的类来存放状态
 *
 * @author AndyChen
 */
public class BookBehavior extends EventSourcedBehavior<BookCommand, BookEvent, BookState> {

    private ActorContext<BookCommand> context;

    public BookBehavior(PersistenceId persistenceId, ActorContext<BookCommand> context) {
        super(persistenceId);
        this.context = context;
    }

    public static Behavior<BookCommand> create(PersistenceId persistenceId) {
        return Behaviors.setup(ctx -> new BookBehavior(persistenceId, ctx));
    }

    @Override
    public BookState emptyState() {
        return new BookState();
    }

    @Override
    public CommandHandler<BookCommand, BookEvent, BookState> commandHandler() {
        return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(AddBook.class, this::onAddBook)
                .onCommand(UpdateBook.class, this::onUpdateBook)
                .onCommand(RemoveBook.class, this::onRemoveBook)
                .onCommand(GetBook.class, this::onGetBook)
                .onCommand(GetAllBook.class, this::onGetAllBook)
                .onCommand(ClearAll.class, this::onClearAll)
                .build();
    }

    @Override
    public EventHandler<BookState, BookEvent> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(BookAdded.class, this::effectBookAdded)
                .onEvent(BookRemoved.class, this::effectBookRemoved)
                .onEvent(AllBookClear.class, this::emptyState)
                .build();
    }

    /**
     * 此方法定义了 Actor 如何响应 Actor 的信号消息.
     *
     * <p>在这里定义了对 {@link RecoveryCompleted} 信号的处理方法
     */
    @Override
    public SignalHandler<BookState> signalHandler() {
        return newSignalHandlerBuilder()
                .onSignal(
                        RecoveryCompleted.class,
                        (state, recoveryCompleted) -> {
                            context.getLog().info("恢复完成={}", state.getBookStore());
                        })
                .build();
    }
    /**
     * -----------------------------------CommandHandler------------------------------------------------------------v
     */

    /**
     * 处理{@link AddBook} 的方法,
     *
     * <p>1. 该命令会产生{@link akka.persistence.typed.javadsl.EffectFactories#persist(Object)} 效果
     *
     * <p>2. 该效果会持久化参数中事件，并且返回 {@link akka.persistence.typed.javadsl.EffectBuilder}
     *
     * <p>3. 上述 Builder 中添加了 {@link akka.persistence.typed.javadsl.EffectBuilder#thenReply(ActorRef,
     * Function)} 回调
     *
     * <p>4. 该回调在 2 结束后执行，其作用时向参数的 Actor 回复了 参数中的响应 {@link StatusReply}
     */
    private Effect<BookEvent, BookState> onAddBook(AddBook command) {
        return Effect()
                .persist(
                        BookAdded.builder() // 持久化事件
                                .uuid(command.getUuid())
                                .title(command.getTitle())
                                .author(command.getAuthor())
                                .price(command.getPrice())
                                .build())
                .thenReply(command.getActorRef(), state -> StatusReply.ack()); // 回复成功
    }

    /**
     * 处理 {@link GetBook} ,如果存在返回具体的 {@link
     * com.iquantex.phoenix.typedactor.guide.persistence.BookState.Book}
     *
     * <p>1. 该命令的效果是 {@link akka.persistence.typed.javadsl.EffectFactories#reply(ActorRef, Object)}
     *
     * <p>2. 该效果会直接回复参数的Actor响应
     */
    private Effect<BookEvent, BookState> onGetBook(BookState state, GetBook cmd) {
        // 判断是否存在
        if (state.getBookStore().containsKey(cmd.getUuid())) {
            return Effect()
                    .reply(
                            cmd.getActorRef(),
                            StatusReply.success(state.getBookStore().get(cmd.getUuid())));
        } else {
            return Effect().reply(cmd.getActorRef(), StatusReply.error("Not Found"));
        }
    }

    /** 处理 {@link GetAllBook} 返回当前 {@link BookState} */
    private Effect<BookEvent, BookState> onGetAllBook(BookState state, GetAllBook cmd) {
        return Effect().reply(cmd.getActorRef(), StatusReply.success(state.getBookStore()));
    }

    /**
     * 处理 {@link RemoveBook} ,移除 {@link BookState} 中的 一本 {@link
     * com.iquantex.phoenix.typedactor.guide.persistence.BookState.Book}
     */
    private Effect<BookEvent, BookState> onRemoveBook(RemoveBook cmd) {
        return Effect().persist(new BookRemoved(cmd.getUuid()));
    }

    /**
     * 处理 {@link UpdateBook} 发出两个事件, {@link BookRemoved} {@link BookAdded},实现更新效果.
     *
     * <p>1. 该效果会原子的持久化事件集合, 直到所有事件持久化后，才会执行下一个 {@link CommandHandler} 的命令
     *
     * <p>2. 在持久化期间的 Command 会被暂存到 {@link EffectFactories#stash()} 中，被暂存起来
     *
     * <p>3. 暂存的 Command 可以通过 {@link EffectFactories#unstashAll()} 取消暂存，直接执行
     */
    private Effect<BookEvent, BookState> onUpdateBook(BookState state, UpdateBook cmd) {
        return Effect()
                .persist(
                        Arrays.asList(
                                new BookRemoved(cmd.getUuid()), // 移除
                                BookAdded.builder() // 添加
                                        .uuid(cmd.getUuid())
                                        .title(cmd.getTitle())
                                        .author(cmd.getAuthor())
                                        .price(cmd.getPrice())
                                        .build()));
    }

    /** 处理 {@link ClearAll} 命令, 产生 {@link AllBookClear} 事件, 该事件会清除当前状态 */
    private Effect<BookEvent, BookState> onClearAll() {
        return Effect().persist(new AllBookClear(Instant.now().toEpochMilli()));
    }

    /**
     * -----------------------------------EventHandler------------------------------------------------------------v
     */
    /**
     * 处理 {@link BookRemoved} 在 {@link BookState} 中移除 {@link Book}, 在 {@link
     * #onRemoveBook(RemoveBook)} 已经做了判断操作
     */
    private BookState effectBookRemoved(BookState state, BookRemoved event) {
        Map<String, Book> bookStore = state.getBookStore();
        bookStore.remove(event.getUuid());
        return new BookState(bookStore); // 新状态
    }

    /** 处理 {@link BookAdded} 添加{@link Book}到 {@link BookState} */
    private BookState effectBookAdded(BookState state, BookAdded event) {
        Map<String, Book> bookStore = state.getBookStore();
        bookStore.put(
                event.getUuid(),
                new Book(event.getUuid(), event.getTitle(), event.getAuthor(), event.getPrice()));
        return new BookState(bookStore); // 新状态
    }
}
