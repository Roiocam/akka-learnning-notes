package com.iquantex.phoenix.typedactor.guide.reliability.protocol;

import akka.actor.typed.ActorRef;
import akka.cluster.sharding.typed.delivery.ShardingProducerController;
import lombok.AllArgsConstructor;
import lombok.Getter;

public interface TodoServiceMessage {

    @AllArgsConstructor
    @Getter
    class UpdateTodo implements TodoServiceMessage {

        private final String listId;
        private final String item;
        private final boolean completed;
        private final ActorRef<TodoResponse> replyTo;
    }

    @AllArgsConstructor
    @Getter
    class Confirmed implements TodoServiceMessage {

        private final ActorRef<TodoResponse> originalReplyTo;
    }

    @AllArgsConstructor
    @Getter
    class TimedOut implements TodoServiceMessage {

        private final ActorRef<TodoResponse> originalReplyTo;
    }

    @AllArgsConstructor
    @Getter
    class WrappedRequestNext implements TodoServiceMessage {

        private final ShardingProducerController.RequestNext<TodoListMessage> next;
    }
}
