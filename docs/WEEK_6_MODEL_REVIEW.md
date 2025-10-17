### A. Multi-Model Architecture
- `AvailableModel` defines id, name, fileName, labels, source (ASSET/DOWNLOADED), version, and input shape (width/height/channels).
- The UI lists `AvailableModels.All`; selecting a model updates `SavedStateHandle`, and the ViewModel builds a `ModelConfig` accordingly.
- `SketchClassifier.loadModel()` reinitializes the interpreter from assets or disk, so swapping models is a metadata-only change for UI.

### B. OTA Delivery
- `ModelManager` manages versions and downloads. For DOWNLOADED models, assets are copied to `filesDir/models/` to simulate OTA.
- `SketchbookViewModel.selectModel()` switches between asset file names and absolute internal paths; the classifier resolves either.

### C. Safety & Integrity
- Versioning prevents stale models: `ModelManager.checkLocalModelStatus()` simulates a remote newer version, prompting an update snackbar.
- Integrity (TODO): after download, compute MD5/SHA-256 and compare to a server-provided hash before activating, to detect corruption/tampering.
- Security: Models are stored in Internal Storage (`context.filesDir`), not public external storage, reducing risk of IP leakage.

### D. Adaptive Pipeline
- Preprocessing and inference adapt to `ModelConfig` provided by the selected model: input size (e.g., 28×28×1 vs 96×96×3) and channels update automatically.
- Post-processing uses the selected model’s labels for confidence display, so UI reflects the active model without hardcoded label sets.
