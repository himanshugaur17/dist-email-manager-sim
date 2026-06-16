# My Learnings From Building A Distributed Email Manager Simulation

The purpose of this project was not to build a perfect email system.

The real purpose was self learning.

I wanted to understand what actually happens when a simple idea like "send an email" is stretched to distributed-system scale. At the start, it feels like the design should be straightforward: take the request, find the server, store the email, and move on. But the deeper i went, the more i realised that the hard part is not sending one email. The hard part is keeping the system correct, available, secure, and debuggable when millions or billions of users are doing it at the same time.

This project became a way for me to think through that journey.

## 1. Email Is Not Just Application Logic

One of the first learnings was that email has a lot of history baked into it.

SMTP was designed in a world where trust assumptions were very different. The protocol has an envelope sender used for routing and a header sender shown to the user. These two can be different, and that gap is one of the reasons spoofing is possible.

So email security is not just "add authentication in the app".

There are layers around the protocol:

- SPF checks whether the sending IP is allowed.
- DKIM signs the message so receivers can detect tampering.
- DMARC ties things together and tells receivers what to do when alignment fails.

This was an important mindset shift for me. Before scaling any system, i need to understand the base protocol properly. Otherwise i may design a beautiful distributed system on top of a weak or misunderstood foundation.

## 2. The Simple Design Breaks Very Fast

The naive design is easy to imagine:

User request -> load balancer -> email server -> database.

This works for learning the flow, but not for large scale.

If all users are sitting behind one giant region or one huge cluster, the blast radius becomes too big. One bad deployment, one config mistake, or one overloaded dependency can affect an enormous number of users.

That is where the idea of cells became important.

A cell is not just a database shard. A cell is a small full-stack deployment. It can have its own routing, compute, and storage boundaries. If one cell is unhealthy, only users mapped to that cell should be affected.

This was a big learning: scaling is not only about adding machines. It is also about reducing the damage when something fails.

## 3. Control Plane And Data Plane Should Not Be Mixed

In the code, i separated cell responsibilities into:

- `CellControlPlane`
- `CellDataPlane`

This helped me understand the difference more clearly.

The data plane handles live user traffic. It should be fast and predictable. It should not suddenly start provisioning infra or doing heavy coordination while a user request is waiting.

The control plane is where administrative work belongs. Adding nodes, removing nodes, health checks, registration, capacity tracking, and migration planning should live there.

In `CellContainer`, the `addCellNode` method has a comment:

> in reality this won't be as simple

That comment is basically the whole lesson.

Adding a node to a distributed system is not just inserting it into a map. Data may need to move. For some time, writes may need to go to both old and new places. Then the remaining gap has to be synced before traffic can safely move.

Earlier i used to think "add node" means capacity increases immediately. Now i see it more like a controlled migration.

## 4. Consistent Hashing Is Useful, But It Is Not Magic

Inside a cell, i used a `TreeMap` to simulate a consistent hash ring:

```java
private final TreeMap<Integer, PhysicalDbNode> nodesConsistentHashRingMap = new TreeMap<>();
```

The current hasher is intentionally simple:

```java
Math.abs(key.hashCode()) % 1000
```

This is good enough for simulation, but it also made me think about what production would need.

A real hash ring needs virtual nodes, better distribution, and careful handling when nodes are added or removed. Also, looking up an exact hash in the map is not enough. In real consistent hashing, if the exact key is not present, we usually move clockwise to the next node in the ring.

So the learning here is: consistent hashing gives a way to reduce remapping, but the implementation details matter a lot.

The bigger point is that hashing does not remove migration complexity. It only controls how much movement happens.

## 5. User To Cell Mapping Is A Core Design Problem

The `TierOneCellRouter` checks where a user belongs:

```java
String cellId = cellUserMapStore.getUserCell(from);
```

If the user is not already mapped, it asks the selection strategy to choose a cell.

This made me think about the global routing problem. At small scale, a map from `userId -> cellId` feels natural. But at very large scale, storing every user mapping in memory everywhere becomes expensive.

One important concept i learnt is virtual buckets.

Instead of mapping every user directly to a physical cell, we can hash users into a fixed number of buckets and map buckets to cells:

User -> bucket -> cell.

This reduces the routing table size a lot. It also makes movement easier because the system can move buckets instead of reasoning about every user individually.

This is one of those ideas that looks small but changes the system design completely.

## 6. Anycast And The Traveler Problem

I added a `GlobalAnycastBasedLb` class to represent the global entry point.

The idea is that a user request can hit the nearest available edge. But "nearest" is not always simple. VPNs, proxies, mobile networks, and global travel can make a user hit an edge far away from their home region.

That means the router cannot assume "this user belongs near me".

A global edge may need enough routing knowledge to send the request to the right home cell. This was a useful learning because it changed how i thought about locality.

Locality is helpful for latency, but correctness still needs a source of truth for where the user's data actually lives.

## 7. ConcurrentHashMap Taught Me About Atomicity

In `InMemCellUserMapStore`, i wrote notes after reading about `ConcurrentHashMap`.

The important learning was that `get` is lock-free for practical purposes, and writes lock only the part of the map being modified. More importantly, if a `put` and `get` happen at the same time, the reader can see the old value or the new value, but not a half-updated value.

That helped me understand atomicity better.

Atomic does not always mean "everyone sees the latest value immediately". It means the operation is indivisible. Other threads do not observe broken intermediate state.

This is a small code-level learning, but it connects directly to distributed systems. Many production systems also have this kind of tradeoff: you may see old state or new state, but the system should avoid exposing corrupted state.

## 8. Updating A Mapping Needs To Be Safer Than It Looks

The current `updateUserCellMapping` method checks the old cell before updating:

```java
if (userCellMap.containsKey(userId) && userCellMap.get(userId).equals(oldCellId)) {
    userCellMap.put(userId, newCellId);
}
```

The intention is correct: only update if the user is still in the expected old cell.

But this also made me realise that check-then-put is not fully atomic as written. In Java, `ConcurrentHashMap` gives better primitives like `replace(key, oldValue, newValue)`.

That is a good learning for me: using a concurrent data structure is not enough by itself. I also need to use the right atomic operation for the exact race i am trying to avoid.

## 9. Thundering Herd Is A Real Capacity Problem

I implemented `PowerOfTwoChoicesStrategy` because of the thundering herd problem.

The comment in the code says:

> the strategy will pick two random cells and choose the one with fewer users

The problem is this: when a new cell is added, many routers may see it as empty and send too much traffic there at the same time. The cell can become overloaded immediately even though the whole point was to add capacity.

Power of two choices is simple but powerful.

Instead of always choosing the globally least-loaded cell, each request randomly samples two cells and picks the better one. It avoids centralized coordination and still gives good load distribution.

This was a nice example of a distributed systems pattern that is practical because it is simple. Sometimes the best design is not perfect global knowledge. Sometimes it is a cheap local decision that behaves well statistically.

## 10. Strategy Pattern Helped Keep Routing Flexible

The `CellSelectionStrategy` interface made the routing logic pluggable:

```java
String selectCellForUser(String userId, List<CellMetadata> availableCells);
```

Today the implementation is power of two choices. Tomorrow it could be:

- least loaded cell
- region-aware placement
- VIP user placement
- capacity leasing
- tenant-aware routing

This is where object-oriented design started making more sense in a distributed systems context.

The point of the interface is not just clean code. The point is that placement logic changes with business and scale requirements. Keeping it behind a strategy makes the router easier to evolve.

## 11. Migration Is The Scariest Part

The migration learning was probably the most important one.

When a node or cell gets full, we cannot just copy data and then flip a switch. While copying is happening, the user may receive new emails, delete old emails, or update state.

So migration needs a safer flow:

1. Start dual writes to old and new locations.
2. Copy historical data in the background.
3. Use CDC or WAL replay to catch up changes that happened during the copy.
4. Apply a small read-only or cutover window if needed.
5. Update the routing mapping atomically.
6. Stop dual writes after confidence checks.

This connects directly to the comment i wrote in `addCellNode`:

> do double writes for a while
> once the gap is too small we can stop double writes
> and sync the remaining data

That is exactly the kind of thing i wanted to learn from this project. The difficult part is not knowing the name of the pattern. The difficult part is understanding why the pattern exists.

## 12. Interfaces Should Match Real Boundaries

I also learnt that abstraction should follow system boundaries.

For example:

- `EmailServer` represents sending email.
- `CellRouter` represents routing.
- `CellControlPlane` represents infrastructure changes.
- `CellDataPlane` represents live traffic handling.
- `CellUserMapStore` represents user placement state.

If all of this was thrown into one big class, the code may still run, but the design would hide the real architecture.

This is an important learning for me. SOLID principles feel much more useful when they are tied to real system pressure. SRP is not just a textbook thing. It helps avoid mixing live request handling with operational control logic.

## 13. What This Project Currently Simulates

This project currently simulates these ideas:

- a global anycast-style load balancer
- tier one routing
- cell-based routing
- user to cell mapping
- in-memory routing state
- cell data plane and control plane separation
- physical DB nodes inside a cell
- simple hashing
- power of two choices for cell selection

It is intentionally not production ready.

There are many simplifications:

- no real SMTP implementation
- no real storage engine
- no durable routing registry
- no CDC pipeline
- no WAL replay
- no health checks
- no true consistent hash ring behavior
- no persistence across restarts
- no tests yet

But that is okay because the goal was learning the shape of the system.

## 14. Things I Would Improve Next

If i continue this project, these are the next things i would like to improve:

- implement proper consistent hash lookup using `ceilingEntry` and wrap-around
- use virtual nodes in the hash ring
- replace check-then-put mapping update with an atomic `replace`
- add tests for routing and cell selection
- store mappings in a more realistic backing store
- add a virtual bucket layer between users and cells
- simulate cell migration with dual writes and cutover
- add health state to `CellMetadata`
- make `PowerOfTwoChoicesStrategy` handle empty cell lists safely
- avoid creating new `Random` instances repeatedly

These improvements are not just code cleanup. Each one maps to a real distributed systems concern.

## 15. My Main Takeaway

The biggest learning from this project is that distributed systems are mostly about managing failure, movement, and uncertainty.

Sending an email is easy.

Routing it correctly when the user can be anywhere is harder.

Keeping the blast radius small is harder.

Moving data without downtime is harder.

Making concurrent updates without corrupting routing state is harder.

And doing all of this while keeping the code understandable is its own separate challenge.

This project helped me move from thinking "how do i make this feature work?" to thinking "how will this system behave when it is under pressure?"

That is the part i want to carry forward.

