package com.iquantex.phoenix.typedactor.guide.persistence;

import com.iquantex.phoenix.typedactor.guide.protocol.Message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Event 表示命令的效果. Event 会被持久化
 *
 * <p>Event在eventHandler处理之后才持久化，因为Event可能执行失败
 *
 * <p>{@link BookBehavior} 的事件接口
 */
public interface BookEvent extends Message {

    @AllArgsConstructor
    @Getter
    @Builder
    class BookAdded implements BookEvent {

        private String uuid;
        private String title;
        private String author;
        private Long price;
    }

    @AllArgsConstructor
    @Getter
    @NoArgsConstructor
    class BookRemoved implements BookEvent {

        private String uuid;
    }

    @AllArgsConstructor
    @Getter
    @NoArgsConstructor
    class AllBookClear implements BookEvent {

        private long timestamp;
    }
}
