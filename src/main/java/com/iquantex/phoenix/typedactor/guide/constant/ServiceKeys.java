package com.iquantex.phoenix.typedactor.guide.constant;

import com.iquantex.phoenix.typedactor.guide.persistence.BookCommand;
import com.iquantex.phoenix.typedactor.guide.protocol.Message;

import akka.actor.typed.receptionist.ServiceKey;

public interface ServiceKeys {

    ServiceKey<Message> DAVID_KEY = ServiceKey.create(Message.class, "David");

    ServiceKey<Message> PARENT_KEY = ServiceKey.create(Message.class, "Parent");

    ServiceKey<BookCommand> BOOK_KEY = ServiceKey.create(BookCommand.class, "Book");
}
