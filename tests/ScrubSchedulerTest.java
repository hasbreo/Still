package com.hasbreo.still;

import java.util.*;

public final class ScrubSchedulerTest {
  static final class Loop implements ScrubScheduler.Queue {
    long now, due;
    Runnable action;

    public void post(Runnable r, long delay) {
      if (action != null) throw new AssertionError("Duplicate scheduled seek");
      action = r;
      due = now + delay;
    }

    public void remove(Runnable r) {
      if (action == r) action = null;
    }

    void until(long time) {
      while (action != null && due <= time) {
        now = due;
        Runnable r = action;
        action = null;
        r.run();
      }
      now = time;
    }
  }

  public static void main(String[] args) {
    Loop loop = new Loop();
    List<Integer> sent = new ArrayList<>();
    ScrubScheduler scheduler = new ScrubScheduler(loop, () -> loop.now, sent::add);
    // 125 Hz input: a 16ms debounce would never dispatch during this entire drag.
    for (int i = 0; i <= 125; i++) {
      loop.until(i * 8);
      scheduler.submit(i);
    }
    if (sent.size() < 30 || sent.size() > 33)
      throw new AssertionError("Continuous drag starved or flooded decoder: " + sent.size());
    loop.until(1040);
    if (sent.get(sent.size() - 1) != 125) throw new AssertionError("Final target lost");
    scheduler.submit(99);
    scheduler.cancel();
    int count = sent.size();
    loop.until(2000);
    if (sent.size() != count) throw new AssertionError("Seek ran after release/cancel");
    scheduler.submit(50);
    scheduler.submit(20);
    loop.until(2040);
    if (sent.get(sent.size() - 1) != 20) throw new AssertionError("Stale target dispatched");
    System.out.println(
        "PASS: 125Hz dragging stays responsive, coalesces latest and cancels on release");
  }
}
