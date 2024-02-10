# Rovehide: Rust duplicate calculator 🦀

Implements the [duplicates detection agent](https://github.com/SebastianAigner/reelchest/blob/9977e6dd821caf3f223bd44215073ea63e3f9e48/backend/src/main/kotlin/agent/Agent.kt) in Rust.

- It's quick!
  - Kotlin: 7m 32s
  - Rust: 40s (~11.3 times faster) ~~52s (~8.7 times faster)~~
- It cross-compiles to my Raspberry Pi (3)!
  - Begin by following [tutorial](https://amritrathie.vercel.app/posts/2020/03/06/cross-compiling-rust-from-macos-to-raspberry-pi/#getting-a-linker). 
  - `rustup target add armv7-unknown-linux-musleabihf`
  - `brew install arm-linux-gnueabihf-binutils`
  - See `.cargo/config` for linker setup
    - Encountered issue with OpenSSL: ` Could not find directory of OpenSSL installation, and this `-sys` crate cannot proceed without this knowledge.`
      - Switched to RusTLS via `reqwest = { version = "0.11.23", features = ["json", "rustls"], default-features = false }`.
    - Encounter issue: ``error occurred: Failed to find tool. Is `arm-linux-musleabihf-gcc` installed?``
      - `brew install FiloSottile/musl-cross/musl-cross`
      - `brew reinstall musl-cross --without-x86_64 --without-aarch64 --with-arm-hf` (workaround for [brew issue](https://github.com/FiloSottile/homebrew-musl-cross/issues/45) `invalid option: --without-x86_64`)
      - `cargo build --target armv7-unknown-linux-musleabihf`
  - 

...as you can see, it's a little quicker on an M2 Max than it is on a Raspberry Pi 3 (shocking, I know 😱)

https://github.com/SebastianAigner/reelchest/assets/2178959/c71ec022-df56-4eb1-ba1b-68a9df0dcbfe


# TODO
- [ ] Use semaphores to not request all hashes at once (right now they're all done in a single burst)
- [ ] Integrate with the persisted duplicates API (once it's done)
- [x] Cross-compile this for my low-end devices (Raspberry Pi 3 / 4)
- [x] Apply [`Arc<str>` optimization](https://www.youtube.com/watch?v=A4cKi7PTJSs)
- [x] Apply early-terminating `mininum_cumulative_sum` to save a bunch of computations (at the expense of tracking state)
- [x] Use `fold_while` from itertools instead of nested loops and evaluate performance
- [x] Split implementation into separate files/modules for better separation of concerns
- [x] Use `num-cpus` to auto-scale worker count
- [ ] Write a blogpost about cross-compiling from macOS to Raspberry Pi
