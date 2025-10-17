### Federated Learning (FL) – Concept
- FL trains a global model across many devices without collecting raw data centrally. A server coordinates training rounds; each device receives a task and current model, performs local training, and returns only model updates for secure aggregation.

### Privacy Benefit
- Raw user data (drawings/handwriting) stays on-device. Only small, anonymized weight updates leave the device. This reduces exposure risk compared to cloud training that uploads full datasets.

### Application to Smart Sketchbook
- The app can adapt to users’ handwriting styles by periodically training locally on recent, consented sketches. Aggregated updates improve the shared model over time without accessing raw drawings on the server.

### FL Data Preparation Architecture
- A dedicated data client prepares local examples (features + labels) for the FL SDK, enforcing quality and anonymization.
- In production, it must ensure: user consent, PII stripping, minimum batch sizes, and robust labeling.

### FL Integration Blueprint
1) Initialization: Device receives the global model and round config from the server.
2) Local Training: The app fine-tunes the model for a few steps using `SketchbookDataClient` batches (small learning rate, few epochs).
3) Reporting: The app sends encrypted, differentially private updates (e.g., gradients) to the server for secure aggregation.

### Future SDK Dependency (Placeholder)
```gradle
// Future TFLite Federated Learning SDK (Placeholder)
// implementation("org.tensorflow:tensorflow-lite-federated:LATEST_VERSION")
```
