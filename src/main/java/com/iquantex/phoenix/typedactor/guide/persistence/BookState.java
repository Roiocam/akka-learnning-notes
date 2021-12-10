package com.iquantex.phoenix.typedactor.guide.persistence;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * State 用于表示 Actor 的状态，这样做的原因之一是Behavior作为行为，只是函数集合，因此使用单独的类来表示Actor的状态。
 *
 * <p>Akka文档中阐述State通常是不可变,EventHandler每次产生新的State
 *
 * <p>State会用于快照保存
 *
 * @author AndyChen
 */
@AllArgsConstructor
@Getter
public final class BookState {

    private final Map<String, Book> bookStore;

    public BookState() {
        this.bookStore = new HashMap<>();
    }

    @AllArgsConstructor
    @Getter
    public static class Book {

        private String uuid;
        private String title;
        private String author;
        private Long price;
    }
}
