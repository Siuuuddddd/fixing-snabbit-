# Live support API

Support tickets are attached to a booking and stored in Support Service's own PostgreSQL database. Before opening a ticket, Support Service asks Booking Service's internal ownership endpoint for the authoritative customer ID. The network call finishes before the local support transaction starts.

## Customer endpoints

All customer endpoints require a customer JWT through the API Gateway. The gateway replaces `X-Authenticated-User-Id` with the verified token subject.

- `POST /api/v1/support/tickets` opens one ticket per booking. Repeating the request for that booking returns the existing ticket without duplicating its initial message.
- `POST /api/v1/support/tickets/{ticketId}/messages` appends a customer message to an open owned ticket.
- `GET /api/v1/support/tickets/{ticketId}/stream?afterMessageId=0` returns `text/event-stream` messages in ascending durable ID order.

Open-ticket request:

```json
{
  "bookingId": "d86fe7e7-e2ca-45fd-82b4-32886ff47d62",
  "subject": "Worker has not arrived",
  "initialMessage": "Please help me contact the assigned worker."
}
```

Message request:

```json
{
  "body": "I am still waiting at the service address."
}
```

The Android client should remember the greatest `messageId` it has received and pass it as `afterMessageId` after reconnecting. Passing zero replays the current history in batches of up to 100. PostgreSQL's increasing message ID is the stream cursor, so the stream remains consistent across application instances.

## Agent endpoints

## Staff support API

Support agents and administrators use role-protected `/api/v1/staff/support/**` routes:

- `GET /tickets?status=OPEN` returns the 100 most recently updated tickets in that state.
- `GET /tickets/{ticketId}/messages?afterMessageId=0` reads durable message history.
- `GET /tickets/{ticketId}/stream?afterMessageId=0` follows new messages with the same reconnectable cursor model as the customer stream.
- `POST /tickets/{ticketId}/messages` sends an agent reply using the staff ID injected from the signed JWT. The request cannot choose an `agentId`.
- `POST /tickets/{ticketId}/resolve` resolves the ticket.

The following APIs remain private service APIs and are deliberately not exposed through the API Gateway:

- `POST /internal/v1/support/tickets/{ticketId}/messages` appends an agent message with `agentId` and `body`.
- `POST /internal/v1/support/tickets/{ticketId}/resolve` closes the ticket.

Resolved tickets reject additional customer and agent messages. Internal automation calls require service authentication, while human agent calls require a short-lived `SUPPORT_AGENT` or `ADMIN` JWT at the gateway.
