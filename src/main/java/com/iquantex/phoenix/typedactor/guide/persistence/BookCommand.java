package com.iquantex.phoenix.typedactor.guide.persistence;

import com.iquantex.phoenix.typedactor.guide.protocol.Message;

import akka.actor.typed.ActorRef;
import akka.actor.typed.RecipientRef;
import akka.actor.typed.Scheduler;
import akka.japi.function.Function;
import akka.pattern.StatusReply;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;

/**
 * Command 是持久化Actor/Behavior 应该接收的消息类型.
 *
 * <p>Command 不会被持久化
 *
 * <p>{@link BookBehavior} 的命令接口
 */
public interface BookCommand extends Message {

    @AllArgsConstructor
    @Getter
    class AddBook implements BookCommand {

        /**
         * 这里消息定义为{@link StatusReply}，作为通用的消息类型，如 {@link
         * akka.actor.typed.javadsl.AskPattern#askWithStatus(RecipientRef, Function, Duration,
         * Scheduler)} 中就用到此消息类型.
         */
        private ActorRef<StatusReply> actorRef;

        private String uuid;
        private String title;
        private String author;
        private Long price;
    }

    @AllArgsConstructor
    @Getter
    class RemoveBook implements BookCommand {

        private ActorRef<StatusReply> actorRef;
        private String uuid;
    }

    @AllArgsConstructor
    @Getter
    class UpdateBook implements BookCommand {

        private ActorRef<StatusReply> actorRef;
        private String uuid;
        private String title;
        private String author;
        private Long price;
    }

    @AllArgsConstructor
    @Getter
    class GetBook implements BookCommand {

        private ActorRef<StatusReply> actorRef;
        private String uuid;
    }

    @AllArgsConstructor
    @Getter
    class GetAllBook implements BookCommand {

        private ActorRef<StatusReply> actorRef;
    }

    @AllArgsConstructor
    @Getter
    class ClearAll implements BookCommand {

        private ActorRef<StatusReply> actorRef;
    }
}
