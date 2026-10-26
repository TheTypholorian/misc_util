package net.typho.misc_util

import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Predicate

open class EventGraph<K : Any, T : Any> {
    inner class EventDependency(
        @JvmField
        val events: (all: List<Event>) -> List<Event>
    )

    inner class Event(
        @JvmField
        val id: K,
        @JvmField
        val event: T,
        @JvmField
        val runThisBefore: MutableList<EventDependency> = mutableListOf(),
        @JvmField
        val runThisAfter: MutableList<EventDependency> = mutableListOf()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EventGraph<*, *>.Event) return false

            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }

        fun before(events: (all: List<Event>) -> List<Event>): EventGraph<K, T>.Event {
            runThisBefore.add(EventDependency(events))
            resolved = false
            return this
        }

        @JvmOverloads
        fun before(predicate: Predicate<Event>, debug: Any, throwIfMissing: Boolean = true) = before {
            val events = it.filter { predicate.test(it) }

            if (throwIfMissing && events.isEmpty()) {
                throw NullPointerException("Event graph does not contain dependency $debug requested by $id")
            }

            events
        }

        @JvmName("beforeId")
        @JvmOverloads
        fun before(id: K, throwIfMissing: Boolean = true) = before({ it.id == id }, id, throwIfMissing)

        @JvmName("beforeEvent")
        @JvmOverloads
        fun before(event: T, throwIfMissing: Boolean = true) = before({ it.event === event }, event, throwIfMissing)

        fun after(events: (all: List<Event>) -> List<Event>): EventGraph<K, T>.Event {
            runThisAfter.add(EventDependency(events))
            resolved = false
            return this
        }

        @JvmOverloads
        fun after(predicate: Predicate<Event>, debug: Any, throwIfMissing: Boolean = true) = after {
            val events = it.filter { predicate.test(it) }

            if (throwIfMissing && events.isEmpty()) {
                throw NullPointerException("Event graph does not contain dependency $debug requested by $id")
            }

            events
        }

        @JvmName("afterId")
        @JvmOverloads
        fun after(id: K, throwIfMissing: Boolean = true) = after({ it.id == id }, id, throwIfMissing)

        @JvmName("afterEvent")
        @JvmOverloads
        fun after(event: T, throwIfMissing: Boolean = true) = after({ it.event === event }, event, throwIfMissing)
    }

    var events = listOf<Event>()
        protected set
    var resolved = true
        protected set

    constructor()

    constructor(vararg entries: SelfAware<K>) {
        entries.forEach { register(it) }
    }

    constructor(vararg entries: Pair<K, T>) {
        entries.forEach { (id, event) -> register(id, event) }
    }

    @Synchronized
    fun register(
        id: K,
        event: T,
    ): Event {
        if (events.any { it.id == id }) {
            throw IllegalArgumentException("Registered duplicate event '$id'")
        }

        val event = Event(id, event)
        events += event
        resolved = false
        return event
    }

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    fun register(event: SelfAware<K>): Event {
        return register(event.id, event as? T ?: throw IllegalArgumentException()).also { event.postRegister(it) }
    }

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    fun register(vararg events: SelfAware<K>) = events.map { register(it) }

    fun execute(out: Consumer<T>) {
        resolve().forEach { out.accept(it.event) }
    }

    fun execute(out: BiConsumer<K, T>) {
        resolve().forEach { out.accept(it.id, it.event) }
    }

    @Synchronized
    fun resolve(): List<Event> {
        if (!resolved) {
            val lookup = events.associateBy { it.id }
            val edges = events.associateWith { mutableSetOf<Event>() }
            val incoming = events.associateWith { 0 }.toMutableMap()

            for (event in events) {
                for (dependency in event.runThisBefore) {
                    val targets = dependency.events(events)

                    for (target in targets) {
                        if (edges[event]!!.add(target)) {
                            incoming[target] = incoming[target]!! + 1
                        }
                    }
                }

                for (dependency in event.runThisAfter) {
                    val targets = dependency.events(events)

                    for (target in targets) {
                        if (edges[target]!!.add(event)) {
                            incoming[event] = incoming[event]!! + 1
                        }
                    }
                }
            }

            val queue = ArrayDeque(events.filter { incoming[it] == 0 })
            val result = mutableListOf<Event>()

            while (queue.isNotEmpty()) {
                val event = queue.removeFirst()
                result.add(event)

                for (next in edges[event]!!) {
                    incoming[next] = incoming[next]!! - 1

                    if (incoming[next] == 0) {
                        queue.addLast(next)
                    }
                }
            }

            if (result.size != events.size) {
                throw IllegalStateException("Event graph contains a cycle")
            }

            events = result
            resolved = true
            return result
        }

        return events
    }

    @Synchronized
    override fun toString(): String {
        return if (events.isEmpty()) {
            "Empty event graph"
        } else {
            events.joinToString(separator = "\n", prefix = if (resolved) "Resolved event graph:\n" else "Unresolved event graph:\n", transform = { "${it.id}[${it.event}]" })
        }
    }

    interface SelfAware<K : Any> {
        val id: K

        fun postRegister(event: EventGraph<K, *>.Event) {
        }
    }
}