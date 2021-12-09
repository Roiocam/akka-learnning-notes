package com.iquantex.phoenix.typedactor.guide.persistence;

import com.iquantex.phoenix.typedactor.guide.protocol.Message;

import akka.actor.typed.ActorRef;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** {@link BookBehavior} 的命令接口 */
public interface BookCommand extends Message {

    @AllArgsConstructor
    @Getter
    class AddBook implements BookCommand {

        /** 这里消息定义为{@link Message}，可以认为所有消息都继承自它，我这么写主要是在外部系统或者不同Actor之间通信时，使用特定的类型会导致耦合 */
        private ActorRef<Message> actorRef;

        private String uuid;
        private String title;
        private String author;
        private Long price;
    }

    @AllArgsConstructor
    @Getter
    class RemoveBook implements BookCommand {

        private ActorRef<Message> actorRef;
        private String uuid;
    }

    @AllArgsConstructor
    @Getter
    class UpdateBook implements BookCommand {

        private ActorRef<Message> actorRef;
        private String uuid;
        private String title;
        private String author;
        private Long price;
    }

    @AllArgsConstructor
    @Getter
    class GetBook implements BookCommand {

        private ActorRef<Message> actorRef;
        private String uuid;
    }

    @AllArgsConstructor
    @Getter
    class GetAllBook implements BookCommand {

        private ActorRef<Message> actorRef;
    }
}
