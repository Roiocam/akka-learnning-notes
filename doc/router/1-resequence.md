# 动机

在 2.1 中提到了 Akka 对消息顺序保证的级别是怎样的，其中多个 Actor 发往一个目的地的顺序是无法保证的。

对于部分需求，如在企业集成中，将 Kafka 作为服务间的消息可靠投递手段，此时如果既希望提高吞吐量（增加 Kafka 分区）

又想做到实现 KafkaProducer -> KafkaConsumer(`actor-pre-parition`) -> ActorSystem 的消息有序，目前来说是无法全局顺序的，因为 Kafka 本身并不保证全局顺序。

这种情况下，则需要在 KafkaConsumer -> ActorSystem -> 实际处理的 Actor 之增加一个重新排序的（业务上）中间层，来实现消息的全局有序，这是消息重定序的其中一个动机。

即**消息重定序**：通过一些手段，将乱序的消息按照一定规律重新排序

# 抽象

重定序需要一些条件，那就是消息排序的规律，在上面企业集成的案例中，则是 KafkaProducer 发送消息的原始顺序，根据用户案例、需求的不同（一般是 Producer 想要的定序的粒度不同），我总结为以下两种模式：

1. **一组消息序列内有序**：消息被定义为一组消息序列，该序列内的消息一定有序
2. **Producer 级别有序**：一个 Producer 到最终处理的 Actor 的消息永远有序
3. **全局有序**：任何 Producer 投递到最终处理的 Actor 的所有消息都是有序的

根据上面的定义，我们大概可以得出一个抽象的概念：`要实现消息有序，消息必须附加一些元数据`。其分别是：

1. **消息序列ID**：表示一组唯一的消息序列
2. **消息当前序号**：消息在当前消息序列中的位置/偏移量
3. **消息总数（可选）**：一组唯一的消息序列内，包含多少个消息

因此，我们可以将消息定义为如下形式：

```protobuf
syntax = "proto2";

message SequenceMessage {
    // 消息序列ID
    required string sequenceId = 1;
    // 消息当前序号
    required int32 sequenceIndex = 2;
    // 消息总数
    optional int32 sequenceTotal = 3;
    // 消息载体（已序列化）
    optional bytes payload = 4;
    // 消息元数据（类名）
    optional string payloadClassName = 5;
}

```

基于上面的信息，关于如何实现消息重定序其实已经有一点眉目了.

# 实现

这里以 `一组消息序列内有序` 为例子（例如切分大消息时，可以使用）

## 1. 定序 Actor 消息定义

```java
@Data
public  class SequenceMessage implements Serializable {

    private static final long serialVersionUID = 6971667486162016022L;

    private String correlationId;
    private Integer index;
    private Integer count;
}

```

## 2. 定序 Actor 内部数据定义

```java

/**

/** 消息序列在内部存储时, 用于维护该序列内所有消息以及当前可被调度发送的索引的状态 */
@Data
public class ResequenceMessage implements Serializable {
    private static final long serialVersionUID = -6102409973523794120L;
    private Integer dispatchableIndex;
    private SequenceMessage[] sequenceMessages;

    /**
    * 递增可调度消息的索引
    *
    * @param dispatchableIndex
    * @return
    */
    public ResequenceMessage advanceTo(int dispatchableIndex) {
        return new ResequenceMessage(dispatchableIndex, sequenceMessages);
    }
}
```

## 3. 定序 Actor 实现

```java

public class ResequenceActor extends AbstractActor {

    private final Map<String, ResequenceMessage> resequenced = new HashMap<>();
    private final ActorRef destination;

    public ResequenceActor(ActorRef destination) {
        this.destination = destination;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().match(SequenceMessage.class, this::handleMessage).build();
    }

    private void handleMessage(SequenceMessage unsequenceMsg) {
        // 定序
        resequence(unsequenceMsg);
        // 调度
        dispatchAllSequenced(unsequenceMsg.getCorrelationId());
        // 移除
        removeCompleted(unsequenceMsg.getCorrelationId());
    }

    /**
    * 移除已完成的消息序列.
    * @param correlationId
    */
    private void removeCompleted(String correlationId) {
        ResequenceMessage resequenceMessage = resequenced.get(correlationId);
        Integer dispatchableIndex = resequenceMessage.getDispatchableIndex();
        Integer count = resequenceMessage.getSequenceMessages()[0].getCount();
        // 如果消息已发送完毕, 那么移除
        if (dispatchableIndex.compareTo(count) > 0) {
            resequenced.remove(correlationId);
        }
    }

    /**
    * 调度当前消息序列中所有已有序的消息.
    * @param correlationId
    */
    private void dispatchAllSequenced(String correlationId) {
        // 一定不为空
        ResequenceMessage resequenceMessage = resequenced.get(correlationId);
        Integer dispatchableIndex = resequenceMessage.getDispatchableIndex();
        for (SequenceMessage sequenceMessage : resequenceMessage.getSequenceMessages()) {
            // 该消息序列满足可被调度的条件（有序）
            if (sequenceMessage.getIndex().equals(dispatchableIndex)) {
                // 这里需要注意, 定序发往目的地 Actor 的消息并不保证可靠交付, 没有 Ack 确认机制下, 可能因为消息丢失而乱序.
                // 如果想做到更准确的有序, 则需要在此之上增加 Ack 确认机制
                destination.tell(sequenceMessage, getSelf());
                dispatchableIndex += 1;
            }
        }
        // 如果已发送, 那么更新自身.
        resequenced.put(correlationId, resequenceMessage.advanceTo(dispatchableIndex));
    }

    /**
    * 重新定序消息序列
    *
    * @param sequenceMsg
    */
    private void resequence(SequenceMessage sequenceMsg) {
        // 取出该消息序列
        ResequenceMessage resequenceMessage =
        resequenced.computeIfAbsent(sequenceMsg.getCorrelationId(), id -> new ResequenceMessage(1, dummyFillSequenceMessage(sequenceMsg.getCount())));
        // 更新消息序列
        SequenceMessage[] sequenceMessages = resequenceMessage.getSequenceMessages();
        sequenceMessages[sequenceMsg.getIndex() - 1] = sequenceMsg;
        resequenceMessage.setSequenceMessages(sequenceMessages);
        resequenced.put(sequenceMsg.getCorrelationId(), resequenceMessage);
    }

    /**
    * 填充空消息
    *
    * @param count
    * @return
    */
    private SequenceMessage[] dummyFillSequenceMessage(Integer count) {
        return IntStream.range(0, count)
        .mapToObj(i -> new SequenceMessage("", -1, count))
        .toArray(SequenceMessage[]::new);
    }
}

```


# 测试

```java

public class ResequenceActorTest {

    @Test
    public void test() {
        ActorSystem system = ActorSystem.create("test");
        TestProbe probe = TestProbe.apply(system);
        ActorRef consumer =
        system.actorOf(
        Props.create(
        ResequenceActor.class, () -> new ResequenceActor(probe.ref())));
        ActorRef router =
        system.actorOf(Props.create(ChaosRouter.class, () -> new ChaosRouter(consumer)));

        for (int i = 1; i <= 5; i++) {
            router.tell(new ResequenceActor.SequenceMessage("A", i, 5), ActorRef.noSender());
        }

        for (int i = 1; i <= 5; i++) {
            router.tell(new ResequenceActor.SequenceMessage("B", i, 5), ActorRef.noSender());
        }

        Seq<Object> seq = probe.receiveN(10, FiniteDuration.apply(10, TimeUnit.SECONDS));
        Collection<Object> allMsg = JavaConverters.asJavaCollection(seq);
        int indexOfA = 1;
        int indexOfB = 1;
        for (Object o : allMsg) {
            Assert.assertTrue(o instanceof ResequenceActor.SequenceMessage);
            String correlationId = ((ResequenceActor.SequenceMessage) o).getCorrelationId();
            if ("A".equals(correlationId)) {
                Assert.assertEquals(
                indexOfA++, ((ResequenceActor.SequenceMessage) o).getIndex().intValue());
            } else if ("B".equals(correlationId)) {
                Assert.assertEquals(
                indexOfB++, ((ResequenceActor.SequenceMessage) o).getIndex().intValue());
            } else {
                fail();
            }
        }
    }

    public static class ChaosRouter extends AbstractActor {
        private final Random random;
        private final ActorRef consumer;

        public ChaosRouter(ActorRef consumer) {
            this.consumer = consumer;
            this.random = new Random(new Date().getTime());
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
            .match(ResequenceActor.SequenceMessage.class, this::chaosMsg)
            .build();
        }

        private void chaosMsg(ResequenceActor.SequenceMessage msg) {
            int mills = random.nextInt(100) + 1;
            getContext()
            .getSystem()
            .getScheduler()
            .scheduleOnce(
            Duration.ofMillis(mills),
            () -> {
                consumer.tell(msg, ActorRef.noSender());
                System.out.println("路由发送消息：" + msg);
            },
            getContext().getDispatcher());
        }
    }
}

```

