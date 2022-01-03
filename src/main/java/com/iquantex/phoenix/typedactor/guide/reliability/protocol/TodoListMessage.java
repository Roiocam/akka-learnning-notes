package com.iquantex.phoenix.typedactor.guide.reliability.protocol;

import akka.actor.typed.ActorRef;
import akka.actor.typed.delivery.ConsumerController;
import lombok.AllArgsConstructor;
import lombok.Getter;

public interface TodoListMessage {

    @AllArgsConstructor
    @Getter
    class AddTask implements TodoListMessage {

        private final String item;
    }

    @AllArgsConstructor
    @Getter
    class CompleteTask implements TodoListMessage {

        private final String item;
    }

    @AllArgsConstructor
    @Getter
    class InitialState implements TodoListMessage {

        private final TodoState state;
    }

    @AllArgsConstructor
    @Getter
    class SaveSuccess implements TodoListMessage {

        private final ActorRef<ConsumerController.Confirmed> confirmedTo;
    }

    @AllArgsConstructor
    @Getter
    class DBError implements TodoListMessage {

        private final Exception cause;
    }

    @AllArgsConstructor
    @Getter
    class CommandDelivery implements TodoListMessage {

        private final TodoListMessage command;
        private final ActorRef<ConsumerController.Confirmed> confirmTo;
    }
}
