package com.iquantex.phoenix.typedactor.guide.persistence;

import com.iquantex.phoenix.typedactor.guide.protocol.Message;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** {@link BookBehavior} 的事件接口, 此接口事件会被持久化 */
public interface BookEvent extends Message {

    @AllArgsConstructor
    @Getter
    class BookAdded implements BookEvent {

        private String uuid;
        private String title;
        private String author;
        private Long price;
    }

    @AllArgsConstructor
    @Getter
    class BookRemoved implements BookEvent {

        private String uuid;
    }
}
