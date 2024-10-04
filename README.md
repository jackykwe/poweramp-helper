# Poweramp Helper
## Installation
1. Install Rust on your computer (`rustup` and `cargo`).
2. Clone this repository.
3. Run `cargo build --release`
4. Use the compiled binary as follows (via `cargo run --release -- <args>`).

## Workflow
To generate auto-generated playlists:
1. Clear `Poweramp Playlists Export` folder on device.
2. Go into Poweramp > Settings > Library > Playlists > Export Poweramp Playlists > choose Target Folder as `Poweramp Playlists Export` > check "Also export file based playlists (.m3u8, .pls, etc.)" > Export.
3. Zip and transfer the `Poweramp Playlists Export` to the computer (at some local location), then extract.
4. Run poweramp-helper, with `-d` pointing at the local location. This will generate `[Auto] <...>.m3u8` files into the local location. You will be prompted before any overwrites happen.
   ```
   cargo run --release -- -d <local_location>
   ```
5. Zip and transfer the local location back to the device.
6. Clear `Poweramp Playlists Import` folder on device.
7. Extract the transferred zip on the device into `Poweramp Playlists Import`.
8. Go into Poweramp > Settings > Library > Rescan.

The auto-generated playlists should be updated in the application.

More usage instructions can be viewed by running the program with the `-h/--help` flag.
