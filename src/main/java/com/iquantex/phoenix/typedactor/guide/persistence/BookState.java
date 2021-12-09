package com.iquantex.phoenix.typedactor.guide.persistence;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 用于持久化.
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
