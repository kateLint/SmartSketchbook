## Week 5 UX Review

### A. Visual Feedback
- Confidence Bars with top prediction highlighting help users understand not just the predicted label but also model certainty across all classes. This transparency builds trust and guides users on whether to retry.

### B. Tactile/Contextual Feedback
- Haptics: Positive haptics for high confidence and subtle feedback for low confidence communicates quality without blocking the flow.
- SnackBar for Low Confidence: Provides non-intrusive, contextual guidance to improve drawings.

### C. Persistence and Navigation
- SavedStateHandle persists lightweight settings (thread count, color) across rotations, keeping the user in control without reconfiguration.
- Navigation Compose structures the app flow cleanly, enabling quick access to Settings and future screens.

### D. A/B Testing Concept (Stroke Width Variants)
- Goal: Identify which stroke width yields clearer input and better model accuracy/UX. The variant flag allows analytics correlation without changing user behavior.


