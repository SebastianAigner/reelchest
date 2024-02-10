# Rovehide: Rust duplicate calculator 🦀

Implements the [duplicates detection agent](https://github.com/SebastianAigner/reelchest/blob/9977e6dd821caf3f223bd44215073ea63e3f9e48/backend/src/main/kotlin/agent/Agent.kt) in Rust.

- It's quick!
  - Kotlin: 7m 32s
  - Rust: 40s (~11.3 times faster) ~~52s (~8.7 times faster)~~

# TODO
- [ ] Use semaphores to not request all hashes at once (right now they're all done in a single burst)
- [ ] Integrate with the persisted duplicates API (once it's done)
- [ ] Cross-compile this for my low-end devices (Raspberry Pi 3 / 4)
- [x] Apply [`Arc<str>` optimization](https://www.youtube.com/watch?v=A4cKi7PTJSs)
- [x] Apply early-terminating `mininum_cumulative_sum` to save a bunch of computations (at the expense of tracking state)
- [x] Use `fold_while` from itertools instead of nested loops and evaluate performance