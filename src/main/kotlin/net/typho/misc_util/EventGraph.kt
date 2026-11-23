package net.typho.misc_util

import java.util.PriorityQueue
import java.util.function.BiConsumer
import java.util.function.Consumer

open class EventGraph<ID : Any, Event : Any> @JvmOverloads constructor(
    @JvmField
    val defaultOrder: Comparator<RegisteredEvent>? = null
) {
    inner class EventDependency(
        @JvmField
        val events: (all: List<RegisteredEvent>) -> List<RegisteredEvent>
    )

    inner class RegisteredEvent(
        @JvmField
        val event: Event,
        @JvmField
        val id: ID
    ) {
        @JvmField
        val runBefore = mutableListOf<EventDependency>()
        @JvmField
        val runAfter = mutableListOf<EventDependency>()

        operator fun component1() = event

        operator fun component2() = id

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EventGraph<*, *>.RegisteredEvent) return false

            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }

        fun runBefore(events: (all: List<RegisteredEvent>) -> List<RegisteredEvent>): EventGraph<ID, Event>.RegisteredEvent {
            runBefore.add(EventDependency(events))
            resolved = false
            return this
        }

        @JvmOverloads
        fun runBefore(predicate: (event: RegisteredEvent) -> Boolean, throwIfMissing: Boolean = true) = runBefore {
            val events = it.filter { predicate(it) }

            if (throwIfMissing && events.isEmpty()) {
                throw NullPointerException("Event graph does not contain dependency requested by $id")
            }

            events
        }

        @JvmName("runBeforeId")
        @JvmOverloads
        fun runBefore(id: ID, throwIfMissing: Boolean = true) = runBefore({ it.id == id }, throwIfMissing)

        @JvmName("runBeforeEvent")
        @JvmOverloads
        fun runBefore(event: Event, throwIfMissing: Boolean = true) = runBefore({ it.event === event }, throwIfMissing)

        fun runAfter(events: (all: List<RegisteredEvent>) -> List<RegisteredEvent>): EventGraph<ID, Event>.RegisteredEvent {
            runAfter.add(EventDependency(events))
            resolved = false
            return this
        }

        @JvmOverloads
        fun runAfter(predicate: (event: RegisteredEvent) -> Boolean, throwIfMissing: Boolean = true) = runAfter {
            val events = it.filter { predicate(it) }

            if (throwIfMissing && events.isEmpty()) {
                throw NullPointerException("Event graph does not contain dependency requested by $id")
            }

            events
        }

        @JvmName("runAfterId")
        @JvmOverloads
        fun runAfter(id: ID, throwIfMissing: Boolean = true) = runAfter({ it.id == id }, throwIfMissing)

        @JvmName("runAfterEvent")
        @JvmOverloads
        fun runAfter(event: Event, throwIfMissing: Boolean = true) = runAfter({ it.event === event }, throwIfMissing)
    }

    var events = listOf<RegisteredEvent>()
        protected set
    var resolved = true
        protected set

    @JvmOverloads
    constructor(defaultOrder: Comparator<RegisteredEvent>? = null, vararg entries: SelfAware<ID>) : this(defaultOrder) {
        entries.forEach { register(it) }
    }

    @JvmOverloads
    constructor(defaultOrder: Comparator<RegisteredEvent>? = null, vararg entries: Pair<ID, Event>) : this(defaultOrder) {
        entries.forEach { (id, event) -> register(id, event) }
    }

    @JvmOverloads
    constructor(defaultOrder: Comparator<RegisteredEvent>? = null, entries: Map<ID, Event>) : this(defaultOrder) {
        entries.forEach { (id, event) -> register(id, event) }
    }

    operator fun get(id: ID) = events.firstOrNull { it.id == id }

    fun register(
        id: ID,
        event: Event,
    ): RegisteredEvent {
        if (events.any { it.id == id }) {
            throw IllegalArgumentException("Registered duplicate event '$id'")
        }

        val event = RegisteredEvent(event, id)
        events += event
        resolved = false
        return event
    }

    @Suppress("UNCHECKED_CAST")
    fun register(event: SelfAware<ID>) = register(event.id, event as? Event ?: throw IllegalArgumentException()).also { event.postRegister(it) }

    fun execute(out: Consumer<Event>) = resolve().forEach { out.accept(it.event) }

    fun execute(out: BiConsumer<ID, Event>) = resolve().forEach { out.accept(it.id, it.event) }

    fun resolve(): List<RegisteredEvent> {
        if (!resolved) {
            val edges = events.associateWith { mutableSetOf<RegisteredEvent>() }
            val incoming = events.associateWith { 0 }.toMutableMap()

            for (event in events) {
                for (dependency in event.runBefore) {
                    val targets = dependency.events(events)

                    for (target in targets) {
                        if (edges[event]!!.add(target)) {
                            incoming[target] = incoming[target]!! + 1
                        }
                    }
                }

                for (dependency in event.runAfter) {
                    val targets = dependency.events(events)

                    for (target in targets) {
                        if (edges[target]!!.add(event)) {
                            incoming[event] = incoming[event]!! + 1
                        }
                    }
                }
            }

            val result = mutableListOf<RegisteredEvent>()

            if (defaultOrder == null) {
                val queue = ArrayDeque(events.filter { incoming[it] == 0 })

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
            } else {
                val queue = PriorityQueue(defaultOrder)
                queue.addAll(events.filter { incoming[it] == 0 })

                while (queue.isNotEmpty()) {
                    val event = queue.remove()
                    result.add(event)

                    for (next in edges[event]!!) {
                        incoming[next] = incoming[next]!! - 1

                        if (incoming[next] == 0) {
                            queue.add(next)
                        }
                    }
                }
            }

            if (result.size != events.size) {
                throw IllegalStateException("Event graph contains a circular dependency")
            }

            events = result
            resolved = true
            return result
        }

        return events
    }

    override fun toString() = if (events.isEmpty()) {
        "Empty event graph"
    } else {
        events.joinToString(separator = "\n", prefix = if (resolved) "Resolved event graph:\n" else "Unresolved event graph:\n", transform = { "${it.id}[${it.event}]" })
    }

    interface SelfAware<K : Any> {
        val id: K

        fun postRegister(event: EventGraph<K, *>.RegisteredEvent) {
        }
    }
}