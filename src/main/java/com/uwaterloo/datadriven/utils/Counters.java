package com.uwaterloo.datadriven.utils;

import java.util.concurrent.atomic.AtomicInteger;

public class Counters {
    public static AtomicInteger fieldCounter = new AtomicInteger(0);
    public static AtomicInteger apiCounter = new AtomicInteger(0);
}
