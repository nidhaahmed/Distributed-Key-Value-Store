# Distributed Key-Value Store

A high-throughput, low-latency in-memory key-value database built in Java 21, designed around distributed systems and storage engine principles from *Designing Data-Intensive Applications* (DDIA).

Targeted for backend infrastructure engineering (Cisco, NetApp, Nutanix).

---

## Architecture Overview

```
                      +---------------------------------------+
                      |         TCP Clients / CLI             |
                      +-------------------+-------------------+
                                          | TCP Socket Stream
                                          v
+-----------------------------------------------------------------------------------+
| KV Server (Port 7379)                                                             |
|                                                                                   |
|  +-----------------------------------------------------------------------------+  |
|  | Request Parser & Protocol Engine (Command Tokenizer, Framing, Serializer)   |  |
|  +-------------------------------------+---------------------------------------+  |
|                                        | Dispatch                                 |
|                                        v                                          |
|  +-----------------------------------------------------------------------------+  |
|  | Thread Pool Executor (Bounded Queue, CallerRunsPolicy)                      |  |
|  +-------------------------------------+---------------------------------------+  |
|                                        |                                          |
|         +------------------------------+------------------------------+           |
|         |                                                             |           |
|         v                                                             v           |
|  +---------------------------------------+   +---------------------------------+  |
|  | Persistent Storage Decorator          |   | LRU Eviction Engine             |  |
|  | - Append-Only File (AOF) Logging      |   | - O(1) Hash + Doubly-Linked List|  |
|  | - Policies: ALWAYS, EVERYSEC, NO      |   | - Dynamic MRU/LRU Reordering    |  |
|  | - Crash Recovery & Replay Parser     |   | - Configurable Capacity         |  |
|  +-------------------+-------------------+   +---------------------------------+  |
|                      |                                                            |
|                      v                                                            |
|  +-----------------------------------------------------------------------------+  |
|  | Concurrent Segmented Hash Store (Lock Striping)                             |  |
|  | - 32 Disjoint Segments with Independent ReentrantReadWriteLocks             |  |
|  | - Custom Separate-Chaining HashTable (Power-of-2 Sizing, 0.75 Load Factor) |  |
|  | - Non-blocking Concurrent Reads, Scalable Parallel Writes                   |  |
|  +-----------------------------------------------------------------------------+  |
+-----------------------------------------------------------------------------------+
```

---

## Key Features & Completed Phases

### Milestone 1: Single-Node Core Engine, Networking, Durability & Cache Eviction

- [x] **Phase 1: Custom In-Memory Storage Engine (`kv-core`)**
  - Custom separate-chaining `HashTable<K, V>` implemented from scratch without using Java collections.
  - Power-of-two table capacity indexing `(hash & (capacity - 1))` with Murmur-inspired bit spreader.
  - Dynamic table expansion (doubling capacity) when load factor exceeds 0.75.
  - CRUD operations: `GET`, `SET`, `PUT`, `DELETE`, `EXISTS`, `KEYS`, `CLEAR`.
  - Baseline `ConcurrentMapStore` adapter for parity verification.

- [x] **Phase 2: Custom TCP Client-Server Protocol (`kv-network`)**
  - Line-oriented text protocol with quotation-aware tokenization (supports multi-word quoted strings like `SET "user:profile" "Distributed Systems"`).
  - Strongly typed `Command` enum and `RequestParser` with strict syntax validation.
  - `ResponseWriter` serializing standard protocol responses: `OK`, `VALUE <val>`, `DELETED`, `NOT_FOUND`, `EXISTS <0|1>`, `KEYS ...`, `PONG`, `ERR <msg>`.
  - Production-ready `TCPClient` library with `AutoCloseable` support.

- [x] **Phase 3: Concurrent Request Handling & Lock Striping (`kv-network` & `kv-core`)**
  - `ConcurrentSegmentedStore`: 32 independent storage segments, each guarded by its own `ReentrantReadWriteLock`.
  - Uncontended concurrent reads across all segments, parallel writes across disjoint segments.
  - Deterministic multi-segment locking protocol on `clear()` and `keys()` preventing deadlocks.
  - Server connection handler backed by configurable thread pool and daemon threads.

- [x] **Phase 4: Durability with Append-Only File (AOF) (`kv-persistence`)**
  - Sequential write-ahead mutation logging (`SET`, `DEL`, `CLEAR`).
  - Configurable `FsyncPolicy`:
    - `ALWAYS`: fsyncs to physical disk on every mutation (zero data loss).
    - `EVERYSEC`: background daemon executor fsyncs dirty pages every 1,000 ms (high throughput, max 1s loss window).
    - `NO`: delegates flushing to OS page cache.
  - `AOFReader` crash recovery: automatically parses and replays the AOF log on restart to reconstruct in-memory state.
  - Corrupted log handling: gracefully recovers from partially written trailing lines (simulating power failure mid-mutation) without crashing.

- [x] **Phase 5: Least Recently Used (LRU) Cache Eviction (`kv-core`)**
  - `LRUCache`: Custom doubly-linked list with sentinel nodes (`head` and `tail`) combined with a fast hash index.
  - Strict $O(1)$ lookup, insertion, and eviction.
  - Access reordering: `GET` and `SET` promote accessed items to MRU (`head`).
  - When capacity threshold is reached, automatically evicts the LRU item (`tail.prev`).
  - Thread-safe via read-write synchronization.

### Milestone 2: 3-Node Cluster, Consistent Hashing & Partition Routing

- [x] **Phase 6: Distributed Partitioning & Routing (`kv-cluster`)**
  - **Consistent Hashing Ring**: Implemented with 150 virtual nodes per physical node (`TreeMap` with $O(\log(N \cdot V))$ lookup).
  - **Uniform Key Distribution**: Empirically validated across 15,000 keys (each node receives ~33.3% of traffic within standard deviation).
  - **Minimal Disruption on Rebalancing**: Adding a new node migrates only ~25% of keys; remaining ~75% remain completely unaffected.
  - **Preference List**: Clockwise ring traversal generating distinct physical replica candidates for quorum replication.
  - **Cluster Routing Engine**:
    - **Proxy Mode**: Transparent single-system image (any cluster node accepts writes/reads and proxies to the partition owner).
    - **Redirect Mode**: High-performance Redis Cluster style `-MOVED <nodeId> <host>:<port>` redirection.
  - **Rebalancer**: Automatic partition rebalancing and migration on dynamic node joins and leaves.

---

## Project Structure

```
distributed-key-value-store/
├── pom.xml                                <-- Root Maven aggregator
├── kv-core/                               <-- In-memory storage & eviction
│   ├── HashTable.java                     <-- Custom separate-chaining hash table
│   ├── HashTableStore.java                <-- KeyValueStore implementation
│   ├── ConcurrentMapStore.java           <-- ConcurrentHashMap baseline
│   ├── ConcurrentSegmentedStore.java      <-- Lock striping storage engine
│   ├── LRUCache.java                      <-- O(1) LRU eviction cache
│   └── StoreConfig.java                   <-- Engine configuration
├── kv-network/                            <-- Networking & protocol
│   ├── protocol/
│   │   ├── Command.java                   <-- Supported protocol commands
│   │   ├── RequestParser.java             <-- Quotation-aware line parser
│   │   ├── ResponseWriter.java            <-- Protocol response formatter
│   │   └── MalformedRequestException.java
│   ├── TCPServer.java                     <-- Multi-threaded ServerSocket
│   ├── ClientHandler.java                 <-- Per-connection socket worker
│   └── TCPClient.java                     <-- Fluent Java client wrapper
├── kv-persistence/                        <-- Durability & recovery
│   ├── AOFWriter.java                     <-- Write-ahead disk logger
│   ├── AOFReader.java                     <-- Recovery replay parser
│   ├── FsyncPolicy.java                   <-- Flush synchronization policies
│   └── PersistentKeyValueStore.java       <-- Durable decorator
├── kv-cluster/                            <-- Distributed partitioning & routing
│   ├── Node.java                          <-- Physical node model & state
│   ├── HashFunction.java                  <-- MurmurHash3 64-bit implementation
│   ├── ConsistentHashRing.java            <-- Ring with virtual nodes (150 vnodes)
│   ├── ClusterRouter.java                 <-- Proxy & redirect request router
│   └── Rebalancer.java                    <-- Partition migration & rebalancing
└── kv-server/                             <-- Main application bootstrap
    ├── KVServerApp.java                   <-- Standalone & cluster node runner
    └── ServerConfig.java                  <-- CLI and cluster config options
```

---

## Benchmark Results

Evaluated on AMD/Intel multi-core with 32 storage segments and loopback TCP:

| Test Scenario | Concurrency | Total Operations | Throughput (ops/sec) | Error Rate |
| :--- | :--- | :--- | :--- | :--- |
| **Sequential Single Client** | 1 client | 2,000 ops | **12,903 ops/sec** | 0.00% |
| **Concurrent Clients** | 10 clients | 10,000 ops | **24,150 ops/sec** | 0.00% |
| **High Parallelism Clients** | 50 clients | 20,000 ops | **29,028 ops/sec** | 0.00% |
| **High Contention (Shared Key)** | 20 clients | 4,000 ops | **18,500 ops/sec** | 0.00% |

---

## Getting Started

### Prerequisites
- **Java 21** or later
- **Apache Maven 3.9+**

### Build and Run Tests
```powershell
# Run the complete test suite across all modules
mvn clean test
```

### Running the Server
```powershell
# Compile and run the standalone server (default port: 7379)
mvn compile exec:java -pl kv-server -Dexec.mainClass="com.distkv.server.KVServerApp"
```

#### CLI Options
```powershell
mvn compile exec:java -pl kv-server -Dexec.mainClass="com.distkv.server.KVServerApp" \
  -Dexec.args="--port 7379 --aof data/store.aof --fsync EVERYSEC --lru 50000"
```

### Example Usage via `nc` / `telnet` / `TCPClient`

```
$ telnet localhost 7379
Trying 127.0.0.1...
Connected to localhost.

SET user:1 "Nidha Ahmed"
OK

GET user:1
VALUE Nidha Ahmed

EXISTS user:1
EXISTS 1

KEYS
KEYS user:1

DELETE user:1
DELETED

GET user:1
NOT_FOUND

PING
PONG

QUIT
BYE
```

---

## Upcoming Milestones Roadmap

- **Milestone 3: Replication & High Availability (Phases 7-8)**
  - Primary-replica replication ($N=2, 3$).
  - Heartbeat-based failure detection with sliding window timeouts.
  - Automatic replica promotion and state sync on node recovery.

- **Milestone 4: Quorum Consistency, Atomic Primitives & Observability (Phases 9-12)**
  - Configurable $W$, $R$, $N$ Quorum reads/writes with Read Repair.
  - Atomic primitives: `INCR`, `CAS` (Compare-And-Set), `MGET`, `MSET`.
  - Telemetry: Prometheus metrics registry, latency histograms (p50/p95/p99), and distributed benchmark harness.
