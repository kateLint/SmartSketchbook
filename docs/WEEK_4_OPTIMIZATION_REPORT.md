## Week 4 – Optimization Report

### Delegate Comparison (steady-state, post warm-up)

| Delegate Path | Inference Time (ms) | Energy Impact | Primary Benefit | Primary Drawback |
|---|---:|---|---|---|
| CPU (Single-Thread) — Day 18 baseline | ~6–12 ms (small model) | High | Universal compatibility | Slowest and highest peak power |
| CPU (Multi-Threaded) — Day 24 fastest | ~3–7 ms | Medium/High | Fast fallback on all devices | More power/heat than delegates |
| GPU Delegate — Day 18 profiled | ~2–5 ms | Medium | Good speed for float models | Init overhead; device/driver variance |
| NNAPI Delegate — Day 22 profiled | ~1–4 ms | Low | Best power efficiency (DSP/NPU) | Requires Android 9+; op support varies |

Notes:
- Values are representative for MNIST-scale models based on profiler logs; exact numbers vary by device/SoC and thread count.
- Energy Impact is the profiler’s qualitative estimate and observed thermals, not absolute mW.

### Final Prioritization Justification
We prioritize NNAPI → GPU → CPU. NNAPI leverages vendor accelerators (DSP/NPU) for the lowest energy per inference and competitive latency. GPU is an excellent fallback for float models with good sustained performance. Multi-threaded CPU remains a robust last-resort that trades speed for higher power/heat. This order best serves a mobile UX where low latency and battery friendliness are both essential.

### Interview Prep – Optimization Review
- NNAPI Fallback: If NNAPI fails despite a compatible model, the most common cause is incomplete operator coverage on the device’s NNAPI driver, forcing parts of the graph back to CPU and breaking delegate application.
- Operator Profiling Value: Per-operator profiling reveals which specific layers (e.g., Conv2D/DepthwiseConv) dominate runtime, information that a single end-to-end timing cannot provide; it shows whether layers were actually delegated or fell back to CPU.
- Memory vs Performance Change (Day 13): Reusing pre-allocated Bitmaps and input/output buffers (FloatBuffer/ByteBuffer) reduces GC churn and allocation spikes, improving memory efficiency and stability rather than raw compute speed.

### Energy Profiling Summary
- Method: Ran 10 inference cycles for CPU (1-thread and multi-thread), GPU, and NNAPI on device using Android Studio Energy Profiler after warm-up.
- Observation: NNAPI showed the lowest Energy Impact bars with consistent latency; GPU was next best; CPU multi-threaded improved latency vs single-thread but showed higher Energy Impact.

### Haptic Feedback (Week 5 preview)
- Approach: Use Jetpack Compose’s `LocalHapticFeedback` to trigger a brief vibration upon classification completion.
- Example (concept):
  - Obtain `val haptics = LocalHapticFeedback.current` and call `haptics.performHapticFeedback(HapticFeedbackType.LongPress)` when a new result arrives.
- Rationale: Subtle tactile confirmation improves perceived responsiveness without visual clutter.


