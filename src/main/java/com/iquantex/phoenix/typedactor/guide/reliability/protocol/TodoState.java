package com.iquantex.phoenix.typedactor.guide.reliability.protocol;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** @author AndyChen */
@Getter
public class TodoState {

    private final List<String> tasks;

    public TodoState(List<String> tasks) {
        this.tasks = Collections.unmodifiableList(tasks);
    }

    public TodoState add(String task) {
        ArrayList<String> copy = new ArrayList<>(tasks);
        copy.add(task);
        return new TodoState(copy);
    }

    public TodoState remove(String task) {
        ArrayList<String> copy = new ArrayList<>(tasks);
        copy.remove(task);
        return new TodoState(copy);
    }
}
