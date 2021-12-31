package com.iquantex.phoenix.typedactor.guide.reliability.typed;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.typed.ActorRef;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.ImgConvertMessage;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.ImgWorkMessage;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.ImgWorkMessage.Covert;
import com.iquantex.phoenix.typedactor.guide.reliability.typed.workpull.ImageConverterActor;
import com.iquantex.phoenix.typedactor.guide.reliability.typed.workpull.WorkManager;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * @author AndyChen
 */
public class WorkPullTest {

    private static ActorTestKit testKit;

    @BeforeAll
    public static void setup() {
        testKit = ActorTestKit.create();
    }

    @Test
    public void test() throws InterruptedException {
        ActorRef<ImgConvertMessage> consumer1 = testKit.spawn(ImageConverterActor.create(),
            "consumer-1");
        ActorRef<ImgConvertMessage> consumer2 = testKit.spawn(ImageConverterActor.create(),
            "consumer-2");
        ActorRef<ImgConvertMessage> consumer3 = testKit.spawn(ImageConverterActor.create(),
            "consumer-3");

        ActorRef<ImgWorkMessage> producer = testKit.spawn(WorkManager.create());
        producer.tell(new Covert("test1", "test1", "test1".getBytes(StandardCharsets.UTF_8)));
        producer.tell(new Covert("test2", "test2", "test2".getBytes(StandardCharsets.UTF_8)));
        producer.tell(new Covert("test3", "test3", "test3".getBytes(StandardCharsets.UTF_8)));
        producer.tell(new Covert("test4", "test4", "test4".getBytes(StandardCharsets.UTF_8)));
        producer.tell(new Covert("test5", "test5", "test5".getBytes(StandardCharsets.UTF_8)));
        producer.tell(new Covert("test6", "test6", "test6".getBytes(StandardCharsets.UTF_8)));

        Thread.sleep(3000);
    }
}
