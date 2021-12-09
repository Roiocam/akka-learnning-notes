package com.iquantex.phoenix.typedactor.guide.controller;

import com.iquantex.phoenix.typedactor.guide.actor.MarketDataActor.Message;
import com.iquantex.phoenix.typedactor.guide.actor.MarketDataActor.Query;
import com.iquantex.phoenix.typedactor.guide.actor.MarketDataActor.State;
import com.iquantex.phoenix.typedactor.guide.actor.MarketDataActor.TrialUpdateValue;
import com.iquantex.phoenix.typedactor.guide.actor.MarketDataActor.UpdateValue;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import com.alibaba.fastjson.JSON;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import static akka.http.javadsl.server.PathMatchers.*;
import static java.util.regex.Pattern.compile;

/** @author AndyChen */
@Singleton
public class MarketDataController extends AllDirectives {
    private ActorRef<Message> ref;
    private Scheduler scheduler;

    @Inject
    public MarketDataController(
            @Named("marketDataActor") ActorRef<Message> ref, Scheduler scheduler) {
        this.ref = ref;
        this.scheduler = scheduler;
    }

    public CompletionStage<HttpResponse> getJsonState() {
        return AskPattern.<Message, State>ask(
                        ref, replyTo -> new Query(replyTo), Duration.ofSeconds(3), scheduler)
                .thenApply(state -> JSON.toJSONString(state))
                .thenApply(json -> HttpResponse.create().withEntity(json));
    }

    public CompletionStage<HttpResponse> updateState(String value) {
        return AskPattern.<Message, State>ask(
                        ref,
                        replyTo -> new UpdateValue(Long.parseLong(value), replyTo),
                        Duration.ofSeconds(3),
                        scheduler)
                .thenApply(state -> JSON.toJSONString(state))
                .thenApply(json -> HttpResponse.create().withEntity(json));
    }

    public CompletionStage<HttpResponse> trialUpdateState(String value) {
        return AskPattern.<Message, State>ask(
                        ref,
                        replyTo -> new TrialUpdateValue(Long.parseLong(value), replyTo),
                        Duration.ofSeconds(3),
                        scheduler)
                .thenApply(state -> JSON.toJSONString(state))
                .thenApply(json -> HttpResponse.create().withEntity(json));
    }

    public Route route() {
        return concat(
                path("market", () -> get(() -> completeWithFuture(getJsonState()))),
                path(
                        segment("market").slash(segment("update").slash(segment(compile("\\d+")))),
                        (value) -> get(() -> completeWithFuture(updateState(value)))),
                path(
                        segment("market")
                                .slash(
                                        segment("trial")
                                                .slash(
                                                        segment("update")
                                                                .slash(segment(compile("\\d+"))))),
                        (value) -> get(() -> completeWithFuture(trialUpdateState(value)))));
    }
}
