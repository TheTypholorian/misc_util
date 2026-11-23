package net.typho.misc_util

class PhasedEventGraph<PhaseID : Any, ID : Any, Event : Any> : EventGraph<PhaseID, EventGraph<ID, Event>> {
    @JvmOverloads
    constructor(
        defaultOrder: Comparator<EventGraph<PhaseID, EventGraph<ID, Event>>.RegisteredEvent>? = null
    ) : super(defaultOrder)

    @JvmOverloads
    constructor(
        defaultOrder: Comparator<EventGraph<PhaseID, EventGraph<ID, Event>>.RegisteredEvent>? = null,
        vararg entries: SelfAware<PhaseID>
    ) : super(defaultOrder, *entries)

    @JvmOverloads
    constructor(
        defaultOrder: Comparator<EventGraph<PhaseID, EventGraph<ID, Event>>.RegisteredEvent>? = null,
        vararg entries: Pair<PhaseID, EventGraph<ID, Event>>
    ) : super(defaultOrder, *entries)

    @JvmOverloads
    constructor(
        defaultOrder: Comparator<EventGraph<PhaseID, EventGraph<ID, Event>>.RegisteredEvent>? = null,
        entries: Map<PhaseID, EventGraph<ID, Event>>
    ) : super(defaultOrder, entries)

    fun register(
        phase: PhaseID,
        id: ID,
        event: Event
    ) = (get(phase) ?: register(phase, EventGraph())).event.register(id, event)

    @Suppress("UNCHECKED_CAST")
    fun register(
        phase: PhaseID,
        event: SelfAware<ID>
    ) = register(phase, event.id, event as? Event ?: throw IllegalArgumentException()).also { event.postRegister(it) }

    fun resolveAll() = resolve().associate { (event, id) -> id to event.resolve() }

    fun flatResolveAll() = resolve().flatMap { (event) -> event.resolve() }
}