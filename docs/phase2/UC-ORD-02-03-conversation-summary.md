# UC-ORD-02 and UC-ORD-03: progress and work split

This note summarizes the discussion from the project use-case status review through the current ORD foundation work.

## Use-case status from the initial repository review

The initial review assessed the code then visible in the repository. It found:

| Use case | Assessment at that review |
| --- | --- |
| UC-REG-01 — Creating account | Mostly implemented. Account creation and persistence existed; successful registration redirected to the dashboard rather than showing a clear confirmation message. |
| UC-REG-02 — Login | Core authentication and role-based routing existed. Some role dashboards were placeholders. |
| UC-REG-03 — Load seeded accounts | Implemented in code; startup loaded configured accounts and checked that all four roles were represented. |
| UC-ORD-01 — Load initial city configuration | Baseline was assessed as implemented in that repository snapshot. |
| UC-ORD-02 — Place an order | No end-to-end order placement flow was found. The dashboard's active order was mock data. |
| UC-ORD-03 — Prepare accepted orders in arrival order | No working preparation queue or start-preparation action was found. |
| UC-ORD-04 — Enforce the order lifecycle | No working order transition logic was found. |
| UC-ORD-05 — Record a state transition with an event id | No working transition-event handling was found. |

This was a static code review, not a test run. The later implementation work took place on the `UC-ORD-02` branch; use the current branch when assessing present status.

## Agreed scope and collaboration split

UC-ORD-02 is shared with a friend; the user is also helping that peer with UC-ORD-03. The proposed effort split was an estimate, not a measure of completed functionality:

- **User, approximately 35%:** create the shared order-domain foundation and repository contract, and coordinate the shared status vocabulary with the UC-ORD-04/05 work.
- **Peer, approximately 65%:** implement the order placement and preparation workflows, including the validation pattern, persistence/API integration, and customer-facing ordering flow as agreed by the team.

For UC-ORD-02, the team discussed **Chain of Responsibility** as a behavioral pattern for order validation. Candidate handlers are customer/session and role validation, restaurant status, item availability, and positive quantity. A handler should either pass the request onward or stop with a refusal reason. The pattern was considered reasonable because later requirements may add or reorder checks; the actual chain is still the peer's implementation work.

UC-ORD-03 needs a per-restaurant FIFO preparation queue of accepted orders, ordered by acceptance time. Starting the next order should move it to `preparing`; the queue must not reject orders for capacity. The queue behavior has not been implemented in the foundation.

## Foundation now on `UC-ORD-02`

The following backend files were added:

- `backend/src/main/java/com/littleisles/domain/ord/Order.java`
- `backend/src/main/java/com/littleisles/domain/ord/OrderLine.java`
- `backend/src/main/java/com/littleisles/domain/ord/OrderStatus.java`
- `backend/src/main/java/com/littleisles/technicalservices/ord/OrderRepository.java`

They provide an immutable order model, an order-line model with a positive quantity and captured unit price, the order status vocabulary, and repository method contracts for inserting/updating/finding orders and retrieving accepted orders in arrival order. The `Order.placed(...)` factory creates the initial unsaved `placed` order required by UC-ORD-02.

These are **foundation classes only**. There is no concrete order repository, order service, HTTP endpoint, or customer ordering UI connected to them. A customer cannot place an order through the web application yet; the frontend and its wiring to the service/API/database belong in the peer's agreed scope.

## Status and persistence coordination

The Phase II lifecycle in UC-ORD-04 describes `placed`, `accepted`, `rejected`, `cancelled`, `preparing`, `ready`, and `picked_up`. The existing database schema also contains `on_the_way` and `delivered`, which are represented in `OrderStatus` for compatibility with that schema; no new delivery behavior was added.

UC-ORD-03 requires accepted-time ordering. The repository contract specifies that ordering, and `Order` has an `acceptedAt` field. The future persistence implementation needs to store that timestamp or obtain it from the UC-ORD-05 transition event. The existing orders schema does not currently have an `accepted_at` column or an event-log table, so the team needs to decide how the persistence adapter will supply it.

The current SQL schema uses numeric product IDs and `groceries_id`; the use-case text says menu-item and restaurant IDs. The repository adapter should map these concepts consistently, or the team should align the model/schema before implementing persistence.

## Verification

Backend compilation completed successfully with Maven tests skipped. No tests were run. The working branch at the time of implementation was `UC-ORD-02`.
