package com.iquantex.phoenix.typedactor.guide.protocol;

import akka.actor.typed.ActorRef;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** @author AndyChen */
@AllArgsConstructor
@Getter
public class HelloResponse implements Message {

    private ActorRef<Message> replyTo;
    private String response;
}
