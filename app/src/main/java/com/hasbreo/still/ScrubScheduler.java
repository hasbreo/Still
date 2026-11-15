package com.hasbreo.still;

import java.util.function.IntConsumer;
import java.util.function.LongSupplier;

/** Throttle, never debounce: a stream of touch events cannot postpone a pending seek. */
final class ScrubScheduler {
  interface Queue {
    void post(Runnable action, long delayMs);

    void remove(Runnable action);
  }

  final Queue queue;
  final LongSupplier clock;
  final IntConsumer seek;
  final Runnable dispatch = this::dispatch;
  int latest;
  boolean pending;
  long lastSent = -1000;

  ScrubScheduler(Queue queue, LongSupplier clock, IntConsumer seek) {
    this.queue = queue;
    this.clock = clock;
    this.seek = seek;
  }

  void submit(int frame) {
    latest = frame;
    if (pending) return;
    pending = true;
    queue.post(dispatch, Math.max(0, 32 - (clock.getAsLong() - lastSent)));
  }

  void dispatch() {
    pending = false;
    lastSent = clock.getAsLong();
    seek.accept(latest);
  }

  void cancel() {
    queue.remove(dispatch);
    pending = false;
  }
}
