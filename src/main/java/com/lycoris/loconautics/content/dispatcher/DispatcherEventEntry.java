package com.lycoris.loconautics.content.dispatcher;

/** A single journal line sent to the dispatcher screen. */
public record DispatcherEventEntry(int kind, String subject, int ageTicks) {
}
