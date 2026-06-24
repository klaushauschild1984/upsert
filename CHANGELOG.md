# Changelog

All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [v0.10.1] — 2026-06-19

### Changed
- `Stage` — no longer closes its `CompiledShader` after dispatch; shader lifetime is the caller's responsibility, enabling reuse across pipeline executions

### Fixed
- `OpenGLStorageBuffer` — `close()` deleted the GL buffer after every dispatch result, destroying persistent GPU-side data (e.g. particle state) across frames
- `OpenGLAtomicCounter` — `bind()` allocated a new GL buffer on every call, resetting the counter and losing accumulated state

## [v0.10.0] — 2026-06-16

### Added
- GPU struct serialization — annotate data classes with `@GpuStruct` for automatic std140/std430 layout without reflection — [docs](README.md#optional-gpu-struct-serialization)

## [v0.9.0] — 2026-06-12

### Added
- `Pipeline` — multi-stage compute dispatch without CPU round-trips between stages — [docs](README.md#pipeline)
- `LongArray` support in `StorageBuffer` (GLSL `int64_t` / `uint64_t`, requires `GL_ARB_gpu_shader_int64`) — [docs](README.md#storage-buffer)
- `kompute-coroutines` module — non-blocking dispatch via Kotlin Coroutines and the `.async()` extension — [docs](README.md#async-dispatch)
- Windows support: EGL-based headless OpenGL context via Mesa3D in CI — [docs](README.md#backends)

### Changed
- `ShaderResult` is now lazy — GPU read-back is deferred until first access, enabling GPU→GPU data flow in pipelines
- `OpenGLStorageBuffer` reuses GPU buffer handles across dispatches via `WeakHashMap`, avoiding redundant uploads for intermediate buffers
- `ContextCreationStrategy` — OpenGL context creation is now customizable and backend-independent
- CI split into per-module jobs with OS matrix for the OpenGL module
- LWJGL dependency management extracted into a shared Gradle convention plugin
- Dependencies migrated to version catalogs
- README simplified — detailed API docs moved to Wiki

## [v0.8.0] — 2026-06-09

### Added
- Mandelbrot renderer in `kompute-showcase` — interactive exploration with real-time pan and zoom - [docs](README.md#mandelbrot-renderer)

### Changed
- **Breaking**: `execution` package renamed to `shader` — update imports from `core.execution.*` to `core.shader.*`
- **Breaking**: `CompiledShader` and `AbstractCompiledShader` moved from `backend` to `shader` package
- **Breaking**: `InternalApi` annotation moved from `backend` to root `core` package

### Fixed
- `StorageBuffer.toString()` no longer calls `mode()` before `validate()`, which could throw

## [v0.7.0] — 2026-06-09

### Changed
- Streamlined API: `ShaderBuilder.compile()` now returns a reusable `CompiledShader`, enabling multi-dispatch without recompilation — [docs](README.md#kotlin)

## [v0.6.0] — 2026-06-08

### Added
- `Image2D` — GPU-side image generation via `imageStore` — [docs](README.md#image2d)

## [v0.5.0] — 2026-06-07

### Added
- `AtomicCounter` — shared GPU counter for parallel accumulation — [docs](README.md#atomic-counter)
- `NamedUniform` — scalar, vector, and matrix uniforms — [docs](README.md#named-uniform)
- `StorageBuffer` read-write mode — [docs](README.md#storage-buffer)
- `kompute-showcase` module with Monte Carlo π approximation — [docs](README.md#showcase)

## [v0.4.0] — 2026-06-06

### Added
- `UniformBufferObject` — structured read-only shader parameters — [docs](README.md#uniform-buffer-object)

### Changed
- LWJGL dependency management via BOM

## [v0.3.0] — 2026-06-05

### Added
- Typed `StorageBuffer<T>` — `FloatArray`, `IntArray`, `DoubleArray`, `ByteArray` — [docs](README.md#storage-buffer)
- Diktat code style — replaces KtLint

### Changed
- Cross-validation for duplicate binding indices

## [v0.2.0] — 2026-06-04

### Added
- Typed exception hierarchy — `KomputeConfigurationException`, `KomputeBackendException`
- Binding validation — index bounds checked against GPU limits
- Detekt static analysis

### Changed
- Automated GitHub Actions release workflow

## [v0.1.0] — 2026-06-03

### Added
- OpenGL compute shader backend via LWJGL — [docs](README.md#getting-started)
- `StorageBuffer` — CPU↔GPU data exchange — [docs](README.md#storage-buffer)
- JMH benchmarks — `kompute-benchmark` module — [docs](README.md#performance)

[v0.10.1]: https://github.com/klaushauschild1984/kompute/compare/v0.10.0...v0.10.1
[v0.10.0]: https://github.com/klaushauschild1984/kompute/compare/v0.9.0...v0.10.0
[v0.9.0]: https://github.com/klaushauschild1984/kompute/compare/v0.8.0...v0.9.0
[v0.8.0]: https://github.com/klaushauschild1984/kompute/compare/v0.7.0...v0.8.0
[v0.7.0]: https://github.com/klaushauschild1984/kompute/compare/v0.6.0...v0.7.0
[v0.6.0]: https://github.com/klaushauschild1984/kompute/compare/v0.5.0...v0.6.0
[v0.5.0]: https://github.com/klaushauschild1984/kompute/compare/v0.4.0...v0.5.0
[v0.4.0]: https://github.com/klaushauschild1984/kompute/compare/v0.3.0...v0.4.0
[v0.3.0]: https://github.com/klaushauschild1984/kompute/compare/v0.2.0...v0.3.0
[v0.2.0]: https://github.com/klaushauschild1984/kompute/compare/v0.1.0...v0.2.0
[v0.1.0]: https://github.com/klaushauschild1984/kompute/releases/tag/v0.1.0
