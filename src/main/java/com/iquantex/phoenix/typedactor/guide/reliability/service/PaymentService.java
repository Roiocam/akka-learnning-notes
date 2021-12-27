package com.iquantex.phoenix.typedactor.guide.reliability.service;

import akka.actor.ActorRef;
import com.iquantex.phoenix.typedactor.guide.reliability.protocol.PaymentMessage.PaySuccess;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author AndyChen
 */
public class PaymentService {

    private static Timer timer = new Timer();
    private static Long delayTime = 6000L;

    public static void updateDelayTime(long time) {
        delayTime = time;
    }

    /**
     * 创建支付任务，延迟之后发送Actor成功请求
     *
     * @param deliverId
     * @param payActor
     */
    public static void createPayTask(Long deliverId, String id, ActorRef payActor) {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                payActor.tell(new PaySuccess(deliverId, id), ActorRef.noSender());
            }
        };
        timer.schedule(task, delayTime);
    }

}
