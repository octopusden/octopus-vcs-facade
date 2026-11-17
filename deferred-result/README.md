# **deferred-result**

A lightweight library for **at-most-once** execution of operations in multi-instance applications.

Ensures that a resource-intensive operation is executed only once across the entire cluster.

If a key already exists, the operation is not executed again. Instead, the stored result is returned immediately, even if it was computed by another instance.

## **Core Idea**

- A resource-intensive operation (e.g., repository analysis) is executed on only one instance.
- All other instances either wait for the result or retrieve it directly from storage.
- No duplicate execution.