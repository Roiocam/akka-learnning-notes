package com.iquantex.phoenix.typedactor.guide.reliability.typed.workpull;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.delivery.WorkPullingProducerController;
import akka.actor.typed.delivery.WorkPullingProducerController.Command;
import akka.actor.typed.delivery.WorkPullingProducerController.RequestNext;
import akka.actor.typed.delivery.WorkPullingProducerController.Start;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.StashBuffer;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.ConversionJob;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.ImgWorkMessage;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.ImgWorkMessage.GetResult;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.ImgWorkMessage.WrapperRequestNext;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * @author AndyChen
 */
@Slf4j
public class WorkManager extends AbstractBehavior<ImgWorkMessage> {

    private final ActorContext<ImgWorkMessage> ctx;
    private final StashBuffer<ImgWorkMessage> stashBuffer;

    public WorkManager(ActorContext<ImgWorkMessage> context,
        StashBuffer<ImgWorkMessage> stashBuffer) {
        super(context);
        this.ctx = context;
        this.stashBuffer = stashBuffer;
    }

    public static Behavior<ImgWorkMessage> create() {
        return Behaviors.setup(ctx -> {
            // 消息转换
            ActorRef<RequestNext<ConversionJob>> messageAdapter = ctx.messageAdapter(
                WorkPullingProducerController.requestNextClass(), WrapperRequestNext::new);
            // 创建 Controller 并启动
            ActorRef<WorkPullingProducerController.Command<ConversionJob>> producerController = ctx.spawn(
                WorkPullingProducerController.create(ConversionJob.class, "workManager",
                    ImageConverterActor.serviceKey,
                    Optional.empty()),
                "WorkPullControllerName"
            );
            producerController.tell(new Start<>(messageAdapter));

            return Behaviors.withStash(
                1000,
                stash -> new WorkManager(ctx, stash)
            );
        });
    }

    @Override
    public Receive<ImgWorkMessage> createReceive() {
        return newReceiveBuilder()
            .onMessage(ImgWorkMessage.Covert.class, this::handleConvert)
            .onMessage(GetResult.class, this::handleGet)
            .onMessage(WrapperRequestNext.class, this::handleRequestNext)
            .build();
    }

    private Behavior<ImgWorkMessage> handleRequestNext(WrapperRequestNext msg) {
        return stashBuffer.unstashAll(active(msg.getNext()));
    }

    private Behavior<ImgWorkMessage> handleGet(GetResult msg) {
        return Behaviors.same();
    }

    /**
     * 暂存转换任务
     */
    private Behavior<ImgWorkMessage> handleConvert(ImgWorkMessage.Covert msg) {
        if (stashBuffer.isFull()) {
            ctx.getLog().warn("Too many Convert requests.");
            return Behaviors.same();
        } else {
            stashBuffer.stash(msg);
            return Behaviors.same();
        }
    }

    private Behavior<ImgWorkMessage> active(
        WorkPullingProducerController.RequestNext<ConversionJob> next) {
        return Behaviors.receive(ImgWorkMessage.class)
            .onMessage(ImgWorkMessage.Covert.class, job -> {
                UUID uuid = UUID.randomUUID();
                log.info("模拟发布工作任务");
                next.sendNextTo().tell(new ConversionJob(uuid, job.getFromFormat(),
                    job.getToFormat(), job.getImage()));
                // 转换成原有的行为
                return createReceive();
            })
            .onMessage(GetResult.class, this::handleGet)
            .onMessage(WrapperRequestNext.class, msg -> {
                // 不期待接收该命令，理应被前置拦截.
                throw new IllegalStateException("Unexpected RequestNext");
            })
            .build();
    }
}
