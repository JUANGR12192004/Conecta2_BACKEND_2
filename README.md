## Conecta2 – Worker Registry

Spring Boot service that manages clients and workers, publishes services, and drives the negotiation of offers. The project focuses on keeping the negotiation workflow consistent and providing endpoints for clients and workers to exchange offers and responses.

### Key endpoints

- `GET /api/v1/clients/{clientId}/offers/pending`: returns the pending offers for a client.
- `GET /api/v1/workers/{workerId}/offers/pending`: returns the active offers a worker has to review.
- `POST /api/v1/offers/{id}/respond`: lets a client accept or reject an offer (`accept` parameter or payload).
- `POST /api/v1/offers/{id}/counter` and `/worker/counter`: register counter-offers from clients or workers.
- `POST /api/v1/offers/{id}/worker/respond`: allows a worker to reply to a client counter-offer.

### Local setup

1. Adjust `src/main/resources/application.properties` with the PostgreSQL credentials and JWT secret required by the service.
2. Start the application with `./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`).
3. Use the REST endpoints above to exercise the negotiation flow.

### Stripe payment integration

- Supply a Stripe test key via `stripe.api-key` (or `STRIPE_API_KEY` env var) and keep `stripe.default-currency=COP`.
- `POST /payment/create-intent` receives JSON body like `{"amount":120000,"description":"Servicio de jardinería","payment_method_types":["card","pse"]}`; the response contains the intent status and `clientSecret`.
- `POST /payment/confirm` expects `{"paymentIntentId":"pi_XXX","paymentMethod":"pm_card_visa"}`; the controller confirms the payment and returns the updated intent.
- `GET /payment/status/{id}` polls Stripe for the latest intent state.
- `POST /payment/offers/{id}/accept-and-create-intent` acepta la oferta (verifica rol `CLIENT`) y crea simultáneamente el `PaymentIntent`; el payload puede contener `payment_method_types` y/o otros campos que luego se usan como metadata para la pasarela.
- `POST /payment/webhook` atiende los eventos de Stripe (`payment_intent.succeeded`, `requires_payment_method`, etc.) y solo actualiza ofertas en estado `PENDIENTE_DE_PAGO`, moviéndolas a `ASIGNADO` si el pago tiene éxito o reabriendo la negociación si falla.
- All payment payloads are handled with `Map<String,Object>` to keep the code DTO-free.
- Exceptions from Stripe propagate via `StripeProcessingException`, so the controller returns HTTP codes that match Stripe's responses.
- Al añadir `PENDIENTE_DE_PAGO` y `ASIGNADO` como nuevos valores de `estado`, regenera el `CHECK` de la tabla `ofertas` ejecutando `scripts/postgres/update-oferta-estado-check.sql` contra tu base PostgreSQL antes de desplegar esta versión.

### Tests

- Run `./mvnw test` (Windows: `mvnw.cmd test`) to execute the unit tests, including `OfertaServiceTest`.
