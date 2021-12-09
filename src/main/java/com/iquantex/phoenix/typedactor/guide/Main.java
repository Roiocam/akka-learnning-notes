package com.iquantex.phoenix.typedactor.guide;

import com.iquantex.phoenix.typedactor.guide.controller.MarketDataController;
import com.iquantex.phoenix.typedactor.guide.modual.ActorSystemModule;

import akka.actor.typed.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;
import com.google.inject.*;

import java.io.IOException;
import java.util.concurrent.CompletionStage;

/** @author AndyChen */
public class Main {

    public static void main(String[] args) throws IOException {
        Injector injector = Guice.createInjector(new ActorSystemModule());
        ActorSystem system = injector.getInstance(ActorSystem.class);
        MarketDataController controller = injector.getInstance(MarketDataController.class);
        startHttpServer(controller.route(), system);
    }

    public static void startHttpServer(Route route, ActorSystem system) throws IOException {
        final Http http = Http.get(system);
        // 监听 Http,
        final CompletionStage<ServerBinding> binding =
                http.newServerAt("localhost", 8080).bind(route);
        System.out.println("Server online at http://localhost:8080/\nPress RETURN to stop...");
        System.in.read(); // let it run until user presses return
        binding.thenCompose(ServerBinding::unbind) // trigger unbinding from the port
                .thenAccept(unbound -> system.terminate()); // and shutdown when done
    }
}
