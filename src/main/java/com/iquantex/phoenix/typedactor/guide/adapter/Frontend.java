package com.iquantex.phoenix.typedactor.guide.adapter;

import com.iquantex.phoenix.typedactor.guide.adapter.Backend.Request;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/** @author AndyChen */
public class Frontend {

    public interface Command {}

    public static class Translate implements Command {

        public final URI site;
        public final ActorRef<URI> replyTo;

        public Translate(URI site, ActorRef<URI> replyTo) {
            this.site = site;
            this.replyTo = replyTo;
        }
    }

    private static class WrappedBackendResponse implements Command {

        final Backend.Response response;

        public WrappedBackendResponse(Backend.Response response) {
            this.response = response;
        }
    }

    public static class Translator extends AbstractBehavior<Command> {

        private final ActorRef<Request> backend;
        private final ActorRef<Backend.Response> backendResponseAdapter;

        private int taskIdCounter = 0;
        private Map<Integer, ActorRef<URI>> inProgress = new HashMap<>();

        public Translator(ActorContext<Command> context, ActorRef<Backend.Request> backend) {
            super(context);
            this.backend = backend;
            this.backendResponseAdapter =
                    context.messageAdapter(Backend.Response.class, WrappedBackendResponse::new);
        }

        public static Behavior<Command> create(ActorRef<Backend.Request> backend) {
            return Behaviors.setup(ctx -> new Translator(ctx, backend));
        }

        @Override
        public Receive<Command> createReceive() {
            return newReceiveBuilder()
                    .onMessage(Translate.class, this::onTranslate)
                    .onMessage(WrappedBackendResponse.class, this::onWrappedBackendResponse)
                    .build();
        }

        private Behavior<Command> onTranslate(Translate cmd) {
            taskIdCounter += 1;
            inProgress.put(taskIdCounter, cmd.replyTo);
            backend.tell(
                    new Backend.StartTranslationJob(
                            taskIdCounter, cmd.site, backendResponseAdapter));
            return this;
        }

        private Behavior<Command> onWrappedBackendResponse(WrappedBackendResponse wrapped) {
            Backend.Response response = wrapped.response;
            if (response instanceof Backend.JobStarted) {
                Backend.JobStarted rsp = (Backend.JobStarted) response;
                getContext().getLog().info("Started {}", rsp.taskId);
            } else if (response instanceof Backend.JobProgress) {
                Backend.JobProgress rsp = (Backend.JobProgress) response;
                getContext().getLog().info("Progress {}", rsp.taskId);
            } else if (response instanceof Backend.JobCompleted) {
                Backend.JobCompleted rsp = (Backend.JobCompleted) response;
                getContext().getLog().info("Completed {}", rsp.taskId);
                inProgress.get(rsp.taskId).tell(rsp.result);
                inProgress.remove(rsp.taskId);
            } else {
                return Behaviors.unhandled();
            }

            return this;
        }
    }
}
