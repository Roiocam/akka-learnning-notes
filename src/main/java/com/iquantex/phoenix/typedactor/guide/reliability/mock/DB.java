package com.iquantex.phoenix.typedactor.guide.reliability.mock;

import com.iquantex.phoenix.typedactor.guide.reliability.protocol.TodoState;

import akka.Done;

import java.util.concurrent.CompletionStage;

/** @author AndyChen */
public interface DB {

    CompletionStage<Done> save(String id, TodoState state);

    CompletionStage<TodoState> load(String id);
}
