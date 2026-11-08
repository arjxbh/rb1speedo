# Design and Android preview tools

Tools are installed locally under ignored `.tools/`. No Android Studio installation is required. The application and its release version are unchanged by this setup.

## Penpot

The local bridge uses Penpot MCP 2.19.0 from the official `2.19.0-RC6` source tag (commit `0333410482785d4dbf40e02bf71083c5f5b24c82`), with its pinned pnpm 12.16.1 and Node from the existing nvm installation. The source is in `.tools/penpot-source/mcp`. The npm stable package was still 2.15.4 when configured, which caused a version warning with the 2.19.0 editor. Codex's user configuration includes `penpot` at `http://localhost:4401/mcp`.

Start the bridge from this directory and leave it running:

```sh
./penpot.sh
```

The upgraded bridge is currently running as the transient user service `rb1-penpot`. Check it with `systemctl --user status rb1-penpot` and stop it with `systemctl --user stop rb1-penpot` before starting a foreground instance. This service is not configured to start automatically after reboot.

After a bridge upgrade, close the Penpot MCP plugin, refresh the editor, reopen the plugin, and reconnect. The editor can otherwise retain the previous plugin code.

1. Sign in at https://design.penpot.app and create a design file named **RB1 Speed**.
2. In the editor, choose **Plugins → Load from URL** and enter `http://localhost:4400/manifest.json`.
3. Run the plugin and click **Connect to MCP server**. Keep the plugin and browser tab open. Allow local-network access if the browser requests it.
4. Restart Codex or reconnect its MCP servers so the new `penpot` tools become available. First ask it to inspect the current Penpot page to verify access.

The local bridge does not need a Penpot account token. Its ports are 4400 (plugin), 4401 (MCP), 4402 (WebSocket), and 4403 (REPL). Stop it with Ctrl+C in its terminal.

Official setup: https://help.penpot.app/mcp/

## Android emulator

The `RB1_API_35` virtual phone uses the Pixel 5 profile and Android 15 / API 35 with Google APIs. It uses KVM for CPU acceleration and software graphics for compatibility. Its state lives under `.tools/avd/`; SDK packages live under `.tools/android-sdk/`.

```sh
./emulator.sh start
```

For automated screenshots without a visible window:

```sh
./emulator.sh start -no-window
```

Run only one instance of this virtual phone at a time. After Android finishes booting, install and launch the current build:

```sh
./build.sh
./emulator.sh adb -e install -r build-native/RB1-Speed.apk
./emulator.sh adb -e shell am start -n club.midlife.rb1speed/.MainActivity
```

Use **DEMO** in the app to preview moving speed without real GPS. The emulator is launched with audio muted; audio routing and real GPS require phone testing.

Capture either orientation (rotation values: 0 = portrait, 1 = landscape):

```sh
./emulator.sh adb -e shell settings put system accelerometer_rotation 0
./emulator.sh adb -e shell settings put system user_rotation 1
./emulator.sh adb -e exec-out screencap -p > /tmp/rb1-landscape.png
./emulator.sh adb -e shell settings put system user_rotation 0
./emulator.sh adb -e exec-out screencap -p > /tmp/rb1-portrait.png
```

Close the emulator window or run `./emulator.sh adb -e emu kill` to stop it. To recreate a missing virtual phone after reinstalling its SDK image, run `./emulator.sh create`.

Official command-line guide: https://developer.android.com/studio/run/emulator-commandline
