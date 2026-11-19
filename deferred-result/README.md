# Deferred result

The library coordinates requests across application instances using Valkey. Use one request ID per logical operation and reuse it for retries.

The first instance to acquire a processing marker queues the operation. 
A separate scheduler renews the marker approximately every third of `processing-ttl` while the operation is queued, running, or publishing its result. 
Renewal checks the owner atomically. A queued worker verifies ownership again before starting the operation. 
Other instances return `PROCESSING` while the marker exists and return the stored outcome for `result-ttl` after publication.

If the owning process stops, pauses, or loses access to Valkey long enough for the marker to expire, a retry can acquire a new marker. 
The original worker may already have changed an external system. A successful external change followed by a failure to publish its result can also lead to repeated execution. 
Operations that need protection against repeated side effects require idempotency at the external system or a way to reconcile their outcome. 
After the result TTL expires, the same request ID can start a new execution.
