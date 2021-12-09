package com.iquantex.phoenix.typedactor.guide.modual;

import com.iquantex.phoenix.typedactor.guide.actor.MarketDataActor;
import com.iquantex.phoenix.typedactor.guide.actor.MarketDataActor.Message;

import com.typesafe.config.ConfigFactory;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import akka.persistence.typed.PersistenceId;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.name.Named;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** @author AndyChen */
public class ActorSystemModule extends AbstractModule {

    public static final ServiceKey<Message> MARKET_DATA_KEY =
            ServiceKey.create(Message.class, "marketData");

    @Override
    public void configure() {
        bind(ActorSystem.class)
                .toInstance(
                        ActorSystem.<Void>create(
                                Behaviors.setup(
                                        ctx -> {
                                            ActorRef<Message> marketDataActor =
                                                    ctx.spawn(
                                                            MarketDataActor.create(
                                                                    PersistenceId.ofUniqueId(
                                                                            "marketData"),
                                                                    ctx),
                                                            "marketData");
                                            ctx.watch(marketDataActor);
                                            // 注入到 receptionist, 用于查询 ActorRef
                                            ctx.getSystem()
                                                    .receptionist()
                                                    .tell(
                                                            Receptionist.register(
                                                                    MARKET_DATA_KEY,
                                                                    marketDataActor));
                                            return Behaviors.empty();
                                            // 这里必须导入config，因为缺少了Journal则持久化Actor不可用，但是程序还可以启动
                                        }),
                                "Main",
                                ConfigFactory.parseResources("reference-simple.conf")));
    }

    @Provides
    @SuppressWarnings("unused")
    public Scheduler scheduler(Injector injector) {
        return injector.getInstance(ActorSystem.class).scheduler();
    }

    @Provides
    @Named("marketDataActor")
    @SuppressWarnings("unused")
    public ActorRef<MarketDataActor.Message> marketDataActor(Injector injector)
            throws ExecutionException, InterruptedException, TimeoutException {
        ActorSystem system = injector.getInstance(ActorSystem.class);
        ActorRef<Receptionist.Command> receptionist = system.receptionist();
        Duration askTimeout = Duration.ofMillis(100);
        CompletionStage<Receptionist.Listing> result =
                AskPattern.ask(
                        receptionist,
                        replyTo -> Receptionist.find(MARKET_DATA_KEY, replyTo),
                        askTimeout,
                        system.scheduler());

        return result.thenApply(
                        reply -> {
                            if (reply != null && reply instanceof Receptionist.Listing) {
                                return reply.getServiceInstances(MARKET_DATA_KEY).stream()
                                        .findFirst()
                                        .get();
                            } else {
                                return null;
                            }
                        })
                .toCompletableFuture()
                .get(100, TimeUnit.MILLISECONDS);
    }
}
