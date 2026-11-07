The spoken Japanese startup greeting now waits five seconds after opening the app, giving Bluetooth/Android Auto audio time to settle before speech begins.

The delay is non-blocking: the gauge and GPS start immediately. Pending speech is canceled if the app is backgrounded or a speed warning takes priority. The greeting still uses an installed offline Japanese voice and does not replay on routine resumes.

Native compilation, both test suites, APK alignment, and signing checks passed locally. Bluetooth/Android Auto clipping and delayed-speech cancellation still need a physical-phone check. Existing signing key preserved for installation over prior versions. Android 8.0 or newer.
