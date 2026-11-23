# Deferred Result

The library coordinates asynchronous operations across multiple application instances using Valkey.

Each logical operation must use a unique request ID, which should be reused for retries.

## How it works

1. The first instance to acquire a processing marker submits the operation to the executor.
2. A separate scheduler renews the marker at intervals of approximately one-third of `processing-ttl` while the operation is queued, running, or its result is being published.
3. Marker renewal atomically verifies ownership. Before execution, the worker also verifies that it still owns the marker.
4. While the processing marker exists, other instances return `PROCESSING`.
5. Once the result is published, subsequent requests return the stored outcome until `result-ttl` expires.

## **Failure Handling**

If a worker cannot verify ownership of the processing marker due to a temporary Valkey outage, it aborts execution and attempts to remove the marker.

If removal fails, the worker retries in the background at the configured `cancel-retry-interval` until the lease expires. 
Once the marker is removed, another request can acquire it without waiting for the full lease duration.

If the owning instance stops, pauses, or loses access to Valkey long enough for the marker to expire,
another instance can acquire a new marker and execute the operation.

## Limitations

**The library does not guarantee exactly-once execution.**

An operation may be executed more than once if:

- The processing marker expires while the original worker is still running.
- An external operation succeeds, but its result cannot be published to Valkey.

Operations with non-repeatable side effects must therefore provide idempotency at the external system or a mechanism to reconcile their outcomes.

After `result-ttl` expires, the same request ID may trigger a new execution.

## Configuration

All configuration properties use the `deferred-result` prefix.

| Property | Default | Description |
|---|---|---|
| `enabled` | `false` | Enables the deferred-result beans. |
| `namespace` | `deferred-result` | Prefix for Valkey keys. |
| `processing-ttl` | `5m` | Processing marker lease duration, renewed at intervals of approximately one-third of this value. |
| `result-ttl` | `5m` | How long a published result is retained. |
| `cancel-retry-interval` | `10s` | Delay between background cancellation retries of the processing marker. |
| `publish-retry.max-attempts` | `5` | Maximum number of attempts to publish a result. |
| `publish-retry.delay` | `1500ms` | Delay between result publication attempts. |
| `valkey.host` | `localhost` | Valkey server hostname. |
| `valkey.port` | `6379` | Valkey server port. |
| `valkey.username` | `null` | Optional Valkey username. |
| `valkey.password` | `null` | Optional Valkey password. |
| `valkey.database` | `0` | Valkey database index. |
| `executor.core-pool-size` | `4` | Core number of threads in the operation executor. |
| `executor.max-pool-size` | `8` | Maximum number of threads in the operation executor. |
| `executor.queue-capacity` | `100` | Maximum number of queued operations. |