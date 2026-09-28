package net.typho.misc_util

import java.util.function.BiConsumer
import java.util.function.Consumer

open class EventGraph<K : Any, T : Any> {
    enum class MissingDependencyBehavior {
        SKIP,
        THROW
    }

    class EventDependency<K : Any>(
        @JvmField
        val id: K,
        @JvmField
        val behavior: MissingDependencyBehavior
    )

    inner class Event(
        @JvmField
        val id: K,
        @JvmField
        val event: T,
        @JvmField
        val runThisBefore: MutableList<EventDependency<K>> = mutableListOf(),
        @JvmField
        val runThisAfter: MutableList<EventDependency<K>> = mutableListOf()
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

        @JvmOverloads
        fun before(id: K, behavior: MissingDependencyBehavior = MissingDependencyBehavior.THROW): EventGraph<K, T>.Event {
            runThisBefore.add(EventDependency(id, behavior))
            resolved = false
            return this
        }

        @JvmOverloads
        fun before(event: SelfAware<K>, behavior: MissingDependencyBehavior = MissingDependencyBehavior.THROW) = before(event.id, behavior)

        @JvmOverloads
        fun after(id: K, behavior: MissingDependencyBehavior = MissingDependencyBehavior.THROW): EventGraph<K, T>.Event {
            runThisAfter.add(EventDependency(id, behavior))
            resolved = false
            return this
        }

        @JvmOverloads
        fun after(event: SelfAware<K>, behavior: MissingDependencyBehavior = MissingDependencyBehavior.THROW) = after(event.id, behavior)
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
                    val target = lookup[dependency.id] ?: when (dependency.behavior) {
                        MissingDependencyBehavior.SKIP -> continue
                        MissingDependencyBehavior.THROW -> throw NullPointerException("Event graph does not contain dependency ${dependency.id} requested by ${event.id}")
                    }

                    if (edges[event]!!.add(target)) {
                        incoming[target] = incoming[target]!! + 1
                    }
                }

                for (dependency in event.runThisAfter) {
                    val source = lookup[dependency.id] ?: when (dependency.behavior) {
                        MissingDependencyBehavior.SKIP -> continue
                        MissingDependencyBehavior.THROW -> throw NullPointerException("Event graph does not contain dependency ${dependency.id} requested by ${event.id}")
                    }

                    if (edges[source]!!.add(event)) {
                        incoming[event] = incoming[event]!! + 1
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