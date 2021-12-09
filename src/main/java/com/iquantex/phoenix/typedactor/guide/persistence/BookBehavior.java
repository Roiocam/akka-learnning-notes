package com.iquantex.phoenix.typedactor.guide.persistence;

import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand.AddBook;
import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand.GetAllBook;
import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand.GetBook;
import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand.RemoveBook;
import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand.UpdateBook;
import com.iquantex.phoenix.typedactor.guide.persistence.BookEvent.BookAdded;
import com.iquantex.phoenix.typedactor.guide.persistence.BookEvent.BookRemoved;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;

/** @author AndyChen */
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
                .onCommand(AddBook.class, (state, cmd) -> Effect().none())
                .onCommand(UpdateBook.class, (state, cmd) -> Effect().none())
                .onCommand(RemoveBook.class, (state, cmd) -> Effect().none())
                .onCommand(GetBook.class, (state, cmd) -> Effect().none())
                .onCommand(GetAllBook.class, (state, cmd) -> Effect().none())
                .build();
    }

    @Override
    public EventHandler<BookState, BookEvent> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(BookAdded.class, (state, event) -> new BookState())
                .onEvent(BookRemoved.class, (state, event) -> new BookState())
                .build();
    }
}
